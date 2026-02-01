package cz.hcasc.dagmarng

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cz.hcasc.dagmarng.net.Api
import cz.hcasc.dagmarng.storage.SecureStore
import cz.hcasc.dagmarng.ui.DagmarNgTheme
import cz.hcasc.dagmarng.ui.EmployeeScreen
import cz.hcasc.dagmarng.ui.UpdateCheckGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val baseUrl: String = BuildConfig.DAGMAR_BASE_URL
        val store = SecureStore(this)

        setContent {
            DagmarNgTheme {
                DagmarApp(baseUrl = baseUrl, store = store)
            }
        }
    }
}

private enum class InstanceState { UNKNOWN, PENDING, ACTIVE, REVOKED, DEACTIVATED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DagmarApp(baseUrl: String, store: SecureStore) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    var instanceId by remember { mutableStateOf(store.getInstanceId()) }
    var instanceToken by remember { mutableStateOf(store.getInstanceToken()) }
    var displayName by remember { mutableStateOf(store.getDisplayName()) }
    var deviceName by remember { mutableStateOf(store.getDeviceName() ?: "") }

    var employmentTemplate by remember { mutableStateOf(Api.EmploymentTemplate.DPP_DPC) }
    var afternoonCutoff by remember { mutableStateOf<String?>(null) }

    var state by remember { mutableStateOf(InstanceState.UNKNOWN) }
    var busy by remember { mutableStateOf(true) }

    fun ensureFingerprint(): String {
        val fp = store.getDeviceFingerprint()
        if (!fp.isNullOrBlank()) return fp
        val n = UUID.randomUUID().toString()
        store.setDeviceFingerprint(n)
        return n
    }

    suspend fun registerIfNeeded(forceNew: Boolean = false) {
        busy = true
        try {
            if (forceNew) {
                store.clearInstanceToken()
                store.clearInstanceId()
                instanceId = null
                instanceToken = null
                state = InstanceState.UNKNOWN
            }

            if (instanceId.isNullOrBlank()) {
                val fp = ensureFingerprint()
                val deviceInfo = JSONObject().apply {
                    put("platform", "ANDROID")
                }
                val resp = Api.registerInstance(
                    clientType = "ANDROID",
                    deviceFingerprint = fp,
                    deviceInfo = deviceInfo,
                    deviceName = deviceName.trim().takeIf { it.isNotBlank() }
                )
                store.setInstanceId(resp.instanceId)
                state = when (resp.status.uppercase()) {
                    "PENDING" -> InstanceState.PENDING
                    "ACTIVE" -> InstanceState.ACTIVE
                    "REVOKED" -> InstanceState.REVOKED
                    "DEACTIVATED" -> InstanceState.DEACTIVATED
                    else -> InstanceState.UNKNOWN
                }
                instanceId = resp.instanceId
            }
        } catch (_: Exception) {
            snackbar.showSnackbar("Registrace zařízení se nepodařila (zkontrolujte internet).")
        } finally {
            busy = false
        }
    }

    suspend fun refreshStatusAndClaimIfNeeded() {
        val id = instanceId ?: return
        busy = true
        try {
            val st = Api.getStatus(id)
            val s = st.status.uppercase()
            state = when (s) {
                "PENDING" -> InstanceState.PENDING
                "ACTIVE" -> InstanceState.ACTIVE
                "REVOKED" -> InstanceState.REVOKED
                "DEACTIVATED" -> InstanceState.DEACTIVATED
                else -> InstanceState.UNKNOWN
            }
            employmentTemplate = st.employmentTemplate
            afternoonCutoff = st.afternoonCutoff
            if (!st.displayName.isNullOrBlank()) {
                store.setDisplayName(st.displayName)
                displayName = st.displayName
            }

            if (state == InstanceState.ACTIVE && instanceToken.isNullOrBlank()) {
                val claim = Api.claimToken(id)
                store.setInstanceToken(claim.instanceToken)
                store.setDisplayName(claim.displayName)
                instanceToken = claim.instanceToken
                displayName = claim.displayName
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        registerIfNeeded(forceNew = false)
        while (true) {
            if (instanceId.isNullOrBlank()) {
                registerIfNeeded(forceNew = false)
                delay(4_000)
                continue
            }
            refreshStatusAndClaimIfNeeded()
            if (!instanceToken.isNullOrBlank()) break
            if (state == InstanceState.REVOKED || state == InstanceState.DEACTIVATED) break
            delay(4_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dagmar NG") },
                actions = {
                    OutlinedButton(
                        onClick = { scope.launch { refreshStatusAndClaimIfNeeded() } },
                        enabled = !busy
                    ) { Text("Aktualizovat") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                store.clearAll()
                                instanceId = null
                                instanceToken = null
                                displayName = null
                                deviceName = ""
                                state = InstanceState.UNKNOWN
                                registerIfNeeded(forceNew = true)
                            }
                        },
                        enabled = !busy && state != InstanceState.DEACTIVATED
                    ) { Text("Nové ID") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        // Update check (dialog)
        UpdateCheckGate(
            baseUrl = baseUrl,
            currentVersionCode = BuildConfig.VERSION_CODE,
            context = ctx
        )

        when {
            busy && instanceId == null -> LoadingScreen(modifier = Modifier.padding(padding))

            state == InstanceState.DEACTIVATED -> AccessLimitedScreen(
                modifier = Modifier.padding(padding),
                instanceId = instanceId
            )

            state == InstanceState.REVOKED -> RevokedScreen(
                modifier = Modifier.padding(padding),
                instanceId = instanceId,
                deviceName = deviceName,
                onDeviceNameChange = {
                    deviceName = it
                    store.setDeviceName(it)
                },
                onTryAgain = { scope.launch { registerIfNeeded(forceNew = true) } }
            )

            instanceToken.isNullOrBlank() -> PendingScreen(
                modifier = Modifier.padding(padding),
                instanceId = instanceId,
                state = state,
                deviceName = deviceName,
                onDeviceNameChange = {
                    deviceName = it
                    store.setDeviceName(it)
                },
                onSubmitNameAndRegister = {
                    scope.launch {
                        registerIfNeeded(forceNew = true)
                    }
                }
            )

            else -> Column(Modifier.padding(padding)) {
                EmployeeScreen(
                    instanceId = instanceId!!,
                    instanceToken = instanceToken!!,
                    displayName = displayName,
                    employmentTemplate = employmentTemplate,
                    afternoonCutoff = afternoonCutoff
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Načítám…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PendingScreen(
    modifier: Modifier = Modifier,
    instanceId: String?,
    state: InstanceState,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    onSubmitNameAndRegister: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Registrace zařízení", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Toto zařízení musí být schváleno adminem. Zadejte jméno zařízení (povinné) a poté vyčkejte na schválení.",
            style = MaterialTheme.typography.bodyMedium
        )
        Divider()

        OutlinedTextField(
            value = deviceName,
            onValueChange = { onDeviceNameChange(it.take(60)) },
            label = { Text("Jméno zařízení (povinné)") },
            placeholder = { Text("např. Recepce – Samsung A54") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onSubmitNameAndRegister,
            enabled = deviceName.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Odeslat / aktualizovat žádost") }

        Spacer(Modifier.height(6.dp))
        Text("ID zařízení:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(instanceId ?: "—", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(10.dp))
        val s = when (state) {
            InstanceState.PENDING, InstanceState.UNKNOWN -> "Čekám na schválení"
            InstanceState.ACTIVE -> "Schváleno"
            InstanceState.REVOKED -> "Zamítnuto"
            InstanceState.DEACTIVATED -> "Přístup omezen"
        }
        Text("Stav: $s", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RevokedScreen(
    modifier: Modifier = Modifier,
    instanceId: String?,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    onTryAgain: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Žádost zamítnuta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Admin zamítl registraci tohoto zařízení. Můžete vygenerovat nové ID a odeslat novou žádost.",
            style = MaterialTheme.typography.bodyMedium
        )
        Divider()
        OutlinedTextField(
            value = deviceName,
            onValueChange = { onDeviceNameChange(it.take(60)) },
            label = { Text("Jméno zařízení (povinné)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onTryAgain,
            enabled = deviceName.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Vygenerovat nové ID a odeslat") }

        Spacer(Modifier.height(8.dp))
        Text("Původní ID: ${instanceId ?: "—"}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccessLimitedScreen(modifier: Modifier = Modifier, instanceId: String?) {
    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PŘÍSTUP OMEZEN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Toto zařízení bylo administrátorem deaktivováno. Docházkové listy zůstávají uloženy, ale přístup je zakázán.",
            style = MaterialTheme.typography.bodyMedium
        )
        Divider()
        Text("ID zařízení: ${instanceId ?: "—"}", style = MaterialTheme.typography.bodySmall)
        Text("V této instanci nelze generovat nové ID ani odeslat novou žádost.", style = MaterialTheme.typography.bodySmall)
    }
}
