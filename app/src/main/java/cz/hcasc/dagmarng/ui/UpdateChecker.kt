package cz.hcasc.dagmarng.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    for (url in candidates) {
        try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .build()
            httpUpdate.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()?.trim().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val vc = json.optInt("version_code", -1)
                        val apkUrl = json.optString("apk_url", "")
                        if (vc > 0 && apkUrl.isNotBlank()) {
                            return@withContext UpdateInfo(
                                versionCode = vc,
                                versionName = json.optString("version_name", null),
                                apkUrl = apkUrl,
                                message = json.optString("message", null)
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // ignore and try next
        }
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
        UpdateBanner(
            info = u,
            onUpdate = { openUrl(context, u.apkUrl) },
            onDismiss = { setDismissed(true) }
        )
    }
}

@Composable
private fun UpdateBanner(info: UpdateInfo, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Je dostupná nová verze Dagmar NG", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(vertical = 4.dp))
            Text(
                buildString {
                    append("Na serveru je novější verze aplikace.")
                    if (!info.versionName.isNullOrBlank()) append(" Verze: ${info.versionName}.")
                    if (!info.message.isNullOrBlank()) append(" ${info.message}")
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.padding(vertical = 6.dp))
            Divider()
            Spacer(Modifier.padding(vertical = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text("Skrýt") }
                Button(onClick = onUpdate) { Text("Stáhnout") }
            }
        }
    }
}
