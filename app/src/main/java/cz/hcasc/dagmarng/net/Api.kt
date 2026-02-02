package cz.hcasc.dagmarng.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Dagmar NG API client.
 * - instance lifecycle (register/status/claim-token)
 * - employee attendance (GET/PUT /api/v1/attendance)
 *
 * Constraints:
 * - No external services.
 * - HTTPS only (canonical: https://dagmar.hcasc.cz).
 */
object Api {

    private const val BASE_URL = "https://dagmar.hcasc.cz"
    private const val API_PREFIX = "/api/v1"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    class ApiException(val code: Int, message: String) : IOException(message)

    data class RegisterResponse(val instanceId: String, val status: String)
    data class ClaimTokenResponse(val instanceToken: String, val displayName: String)

    enum class EmploymentTemplate {
        DPP_DPC, HPP;
        companion object {
            fun fromWire(v: String?): EmploymentTemplate =
                when ((v ?: "").uppercase()) {
                    "HPP" -> HPP
                    else -> DPP_DPC
                }
        }
    }

    data class InstanceStatus(
        val status: String,
        val displayName: String? = null,
        val employmentTemplate: EmploymentTemplate = EmploymentTemplate.DPP_DPC,
        val afternoonCutoff: String? = null
    )

    data class AttendanceDay(
        val date: String,
        val arrivalTime: String?,
        val departureTime: String?,
        val plannedArrivalTime: String?,
        val plannedDepartureTime: String?
    )

    data class AttendanceMonthResponse(
        val days: List<AttendanceDay>,
        val instanceDisplayName: String? = null
    )

    data class AttendanceUpsertBody(
        val date: String,
        val arrivalTime: String?,
        val departureTime: String?
    )

    suspend fun registerInstance(
        clientType: String,
        deviceFingerprint: String,
        deviceInfo: JSONObject? = null,
        displayName: String? = null
    ): RegisterResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("client_type", clientType)
            put("device_fingerprint", deviceFingerprint)
            if (deviceInfo != null) put("device_info", deviceInfo)
            if (!displayName.isNullOrBlank()) put("display_name", displayName)
        }
        val req = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/instances/register")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "register failed: ${resp.code} $body")
                throw ApiException(resp.code, "register failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            RegisterResponse(
                instanceId = json.getString("instance_id"),
                status = json.getString("status")
            )
        }
    }

    suspend fun getStatus(instanceId: String): InstanceStatus = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/instances/${instanceId}/status")
            .get()
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "status failed: ${resp.code} $body")
                throw ApiException(resp.code, "status failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            InstanceStatus(
                status = json.optString("status", ""),
                displayName = json.optString("display_name", null),
                employmentTemplate = EmploymentTemplate.fromWire(json.optString("employment_template", null)),
                afternoonCutoff = json.optString("afternoon_cutoff", null)
            )
        }
    }

    suspend fun claimToken(instanceId: String): ClaimTokenResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/instances/${instanceId}/claim-token")
            .post("{}".toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "claim-token failed: ${resp.code} $body")
                throw ApiException(resp.code, "claim-token failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            ClaimTokenResponse(
                instanceToken = json.getString("instance_token"),
                displayName = json.getString("display_name")
            )
        }
    }

    suspend fun getAttendanceMonth(
        year: Int,
        month: Int,
        instanceToken: String
    ): AttendanceMonthResponse = withContext(Dispatchers.IO) {
        val mm = month.toString().padStart(2, '0')
        val url = BASE_URL + API_PREFIX + "/attendance?year=$year&month=$mm"
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $instanceToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "attendance GET failed: ${resp.code} $body")
                throw ApiException(resp.code, "attendance GET failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            val daysJson = json.optJSONArray("days")
            val out = ArrayList<AttendanceDay>(daysJson?.length() ?: 0)
            if (daysJson != null) {
                for (i in 0 until daysJson.length()) {
                    val d = daysJson.getJSONObject(i)
                    out.add(
                        AttendanceDay(
                            date = d.getString("date"),
                            arrivalTime = d.optString("arrival_time", null),
                            departureTime = d.optString("departure_time", null),
                            plannedArrivalTime = d.optString("planned_arrival_time", null),
                            plannedDepartureTime = d.optString("planned_departure_time", null)
                        )
                    )
                }
            }
            AttendanceMonthResponse(
                days = out,
                instanceDisplayName = json.optString("instance_display_name", null)
            )
        }
    }

    suspend fun putAttendance(
        body: AttendanceUpsertBody,
        instanceToken: String
    ): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("date", body.date)
            if (body.arrivalTime == null) put("arrival_time", JSONObject.NULL) else put("arrival_time", body.arrivalTime)
            if (body.departureTime == null) put("departure_time", JSONObject.NULL) else put("departure_time", body.departureTime)
        }

        val req = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/attendance")
            .put(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $instanceToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "attendance PUT failed: ${resp.code} $respBody")
                throw ApiException(resp.code, "attendance PUT failed: HTTP ${resp.code}")
            }
        }
    }
}
