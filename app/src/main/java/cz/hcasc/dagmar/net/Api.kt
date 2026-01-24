package cz.hcasc.dagmar.net

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
 * Minimal HTTP client for DAGMAR instance lifecycle.
 *
 * Constraints:
 * - No external services.
 * - HTTPS only (production domain: https://dagmar.hcasc.cz).
 * - This client only handles instance registration and token claiming.
 *   Attendance is handled by the web app inside WebView.
 */
object Api {

    // IMPORTANT: canonical domain
    private const val BASE_URL = "https://dagmar.hcasc.cz"
    private const val API_PREFIX = "/api/v1"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        // Intentionally no logging interceptor to avoid leaking tokens into logs.
        .build()

    data class RegisterResponse(
        val instanceId: String,
        val status: String
    )

    data class ClaimTokenResponse(
        val instanceToken: String,
        val displayName: String
    )

    suspend fun registerInstance(
        clientType: String,
        deviceFingerprint: String,
        deviceInfo: JSONObject? = null,
        deviceName: String? = null
    ): RegisterResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("client_type", clientType)
            put("device_fingerprint", deviceFingerprint)
            if (deviceInfo != null) {
                put("device_info", deviceInfo)
            }
        }

        val builder = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/instances/register")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
        if (!deviceName.isNullOrBlank()) {
            builder.header("X-Device-Name", deviceName)
        }
        val req = builder.build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "register failed: ${resp.code} $body")
                throw IOException("register failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            RegisterResponse(
                instanceId = json.getString("instance_id"),
                status = json.getString("status")
            )
        }
    }

    /**
     * Status endpoint (optional for Android; WebView will typically handle this).
     */
    suspend fun getStatus(instanceId: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(BASE_URL + API_PREFIX + "/instances/${instanceId}/status")
            .get()
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w("DagmarApi", "status failed: ${resp.code} $body")
                throw IOException("status failed: HTTP ${resp.code}")
            }
            JSONObject(body)
        }
    }

    /**
     * Client periodically calls this after admin activation.
     *
     * Backend returns token only for ACTIVE instances.
     */
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
                throw IOException("claim-token failed: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            ClaimTokenResponse(
                instanceToken = json.getString("instance_token"),
                displayName = json.getString("display_name")
            )
        }
    }
}
