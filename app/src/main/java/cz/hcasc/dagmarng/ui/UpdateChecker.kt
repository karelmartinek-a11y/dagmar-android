package cz.hcasc.dagmarng.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String?,
    val apkUrl: String,
    val message: String?
)

private val httpUpdate: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(7, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS)
    .build()

private suspend fun fetchUpdateInfo(baseUrl: String): UpdateInfo? = withContext(Dispatchers.IO) {
    val candidates = listOf(
        baseUrl.trimEnd('/') + "/android-version.json",
        baseUrl.trimEnd('/') + "/download/dochazka-dagmar-ng.json",
        baseUrl.trimEnd('/') + "/download/dochazka-dagmar.json"
    )

    fun fetchOnce(url: String): UpdateInfo? {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .build()
            httpUpdate.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.trim().orEmpty()
                if (body.isBlank()) return null
                val json = JSONObject(body)
                val vc = json.optInt("version_code", -1)
                val apkUrl = json.optString("apk_url", "")
                if (vc <= 0 || apkUrl.isBlank()) return null
                UpdateInfo(
                    versionCode = vc,
                    versionName = json.optString("version_name", null),
                    apkUrl = apkUrl,
                    message = json.optString("message", null)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    for (url in candidates) {
        val info = fetchOnce(url)
        if (info != null) return@withContext info
    }
    null
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // ignore
    }
}

@Composable
fun UpdateCheckGate(
    baseUrl: String,
    currentVersionCode: Int,
    context: Context
) {
    val (update, setUpdate) = remember { mutableStateOf<UpdateInfo?>(null) }
    val (dismissed, setDismissed) = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val u = fetchUpdateInfo(baseUrl)
        if (u != null && u.versionCode > currentVersionCode) {
            setUpdate(u)
        }
    }

    val u = update
    if (u != null && !dismissed) {
        AlertDialog(
            onDismissRequest = { setDismissed(true) },
            title = { Text("Je dostupná nová verze Dagmar NG") },
            text = {
                Text(
                    buildString {
                        append("Na serveru je novější verze aplikace.")
                        if (!u.versionName.isNullOrBlank()) append("\n\nVerze: ${u.versionName}")
                        if (!u.message.isNullOrBlank()) append("\n\n${u.message}")
                        append("\n\nChcete stáhnout aktualizaci?")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { openUrl(context, u.apkUrl) }) { Text("Aktualizovat") }
            },
            dismissButton = {
                TextButton(onClick = { setDismissed(true) }) { Text("Později") }
            }
        )
    }
}
