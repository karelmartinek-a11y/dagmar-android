package cz.hcasc.dagmarng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hcasc.dagmarng.net.Api
import cz.hcasc.dagmarng.net.Api.EmploymentTemplate
import cz.hcasc.dagmarng.util.AttendanceRowLike
import cz.hcasc.dagmarng.util.computeDayCalc
import cz.hcasc.dagmarng.util.computeMonthStats
import cz.hcasc.dagmarng.util.parseCutoffToMinutes
import cz.hcasc.dagmarng.util.workingDaysInMonthCs
import kotlinx.coroutines.delay
import java.util.Calendar

private fun pad2(n: Int) = n.toString().padStart(2, '0')

private fun yyyyMm(cal: Calendar): String {
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return "$y-${pad2(m)}"
}

private fun monthLabelCs(yyyyMm: String): String {
    val parts = yyyyMm.split("-")
    val y = parts[0].toInt()
    val m = parts[1].toInt()
    val months = arrayOf("leden","únor","březen","duben","květen","červen","červenec","srpen","září","říjen","listopad","prosinec")
    return "${months[m-1]} $y"
}

private fun isoToday(): String {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "$y-${pad2(m)}-${pad2(d)}"
}

private fun daysInMonth(year: Int, month1: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(year, month1 - 1, 1, 0, 0, 0)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun toDowLabelCs(dateIso: String): String {
    val (y, m, d) = dateIso.split("-").map { it.toInt() }
    val cal = Calendar.getInstance()
    cal.set(y, m - 1, d, 0, 0, 0)
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "po"
        Calendar.TUESDAY -> "út"
        Calendar.WEDNESDAY -> "st"
        Calendar.THURSDAY -> "čt"
        Calendar.FRIDAY -> "pá"
        Calendar.SATURDAY -> "so"
        else -> "ne"
    }
}

private fun normalizeTime(value: String): String {
    val v = value.trim()
    if (v.isBlank()) return ""
    if (Regex("^\\d{4}$").matches(v)) {
        val hh = v.substring(0, 2).toIntOrNull() ?: return v
        val mm = v.substring(2, 4).toIntOrNull() ?: return v
        if (hh in 0..23 && mm in 0..59) return "${pad2(hh)}:${pad2(mm)}"
        return v
    }
    val colon = Regex("^(\\d{1,2}):(\\d{2})$").find(v)
    if (colon != null) {
        val hh = colon.groupValues[1].toIntOrNull() ?: return v
        val mm = colon.groupValues[2].toIntOrNull() ?: return v
        if (hh in 0..23 && mm in 0..59) return "${pad2(hh)}:${pad2(mm)}"
        return v
    }
    if (Regex("^\\d{1,2}$").matches(v)) {
        val hh = v.toIntOrNull()
        if (hh != null && hh in 1..23) return "${pad2(hh)}:00"
    }
    return v
}

private fun isValidTimeOrEmpty(value: String): Boolean {
    val v = normalizeTime(value)
    if (v.isBlank()) return true
    return Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matches(v)
}

private fun formatHours(mins: Int): String = String.format("%.1f", mins / 60.0)

private data class DayRow(
    val date: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val plannedArrivalTime: String?,
    val plannedDepartureTime: String?
)

private data class QueueItem(val date: String, val arrivalTime: String?, val departureTime: String?)

@Composable
fun EmployeeScreen(
    instanceId: String,
    instanceToken: String,
    displayName: String?,
    employmentTemplate: EmploymentTemplate,
    afternoonCutoff: String?
) {
    val today = isoToday()
    val cutoffMinutes = remember(afternoonCutoff) { parseCutoffToMinutes(afternoonCutoff, "17:00") }

    var month by remember { mutableStateOf(yyyyMm(Calendar.getInstance())) }
        var refreshNonce by remember { mutableStateOf(0) }
        var punchNonce by remember { mutableStateOf(0) }
    var monthLocked by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    val rows = remember { mutableStateListOf<DayRow>() }
    val queue = remember { mutableStateListOf<QueueItem>() } // in-memory only

    suspend fun loadMonth() {
        val (y, m) = month.split("-").let { it[0].toInt() to it[1].toInt() }
        try {
            val res = Api.getAttendanceMonth(y, m, instanceToken)
            val dim = daysInMonth(y, m)
            val byDate = HashMap<String, Api.AttendanceDay>()
            for (d in res.days) byDate[d.date] = d
            rows.clear()
            for (day in 1..dim) {
                val date = "$y-${pad2(m)}-${pad2(day)}"
                val dd = byDate[date]
                rows.add(
                    DayRow(
                        date = date,
                        arrivalTime = dd?.arrivalTime,
                        departureTime = dd?.departureTime,
                        plannedArrivalTime = dd?.plannedArrivalTime,
                        plannedDepartureTime = dd?.plannedDepartureTime
                    )
                )
            }
            monthLocked = false
            online = true
        } catch (e: Api.ApiException) {
            if (e.code == 423) {
                monthLocked = true
                rows.clear()
                online = true
            } else {
                rows.clear()
                monthLocked = false
                online = false
            }
        } catch (_: Exception) {
            rows.clear()
            monthLocked = false
            online = false
        }
    }

    suspend fun flushQueueIfPossible() {
        if (!online) return
        if (sending) return
        if (queue.isEmpty()) return
        sending = true
        try {
            while (queue.isNotEmpty()) {
                val item = queue.first()
                Api.putAttendance(
                    Api.AttendanceUpsertBody(item.date, item.arrivalTime, item.departureTime),
                    instanceToken
                )
                queue.removeAt(0)
            }
        } catch (_: Exception) {
            // keep remaining
        } finally {
            sending = false
        }
    }

    suspend fun upsert(date: String, arrival: String?, departure: String?) {
        if (!online) {
            val idx = queue.indexOfFirst { it.date == date }
            if (idx >= 0) queue[idx] = QueueItem(date, arrival, departure) else queue.add(QueueItem(date, arrival, departure))
            return
        }
        try {
            Api.putAttendance(Api.AttendanceUpsertBody(date, arrival, departure), instanceToken)
        } catch (_: Exception) {
            val idx = queue.indexOfFirst { it.date == date }
            if (idx >= 0) queue[idx] = QueueItem(date, arrival, departure) else queue.add(QueueItem(date, arrival, departure))
        } finally {
            flushQueueIfPossible()
        }
    }

    suspend fun onChangeTime(date: String, field: String, rawValue: String) {
        if (monthLocked) return
        val trimmed = normalizeTime(rawValue)
        if (!isValidTimeOrEmpty(trimmed)) return

        val idx = rows.indexOfFirst { it.date == date }
        if (idx < 0) return
        val cur = rows[idx]
        val next = if (field == "arrival") cur.copy(arrivalTime = if (trimmed.isBlank()) null else trimmed)
        else cur.copy(departureTime = if (trimmed.isBlank()) null else trimmed)
        rows[idx] = next

        val arrival = if (field == "arrival") next.arrivalTime else cur.arrivalTime
        val departure = if (field == "departure") next.departureTime else cur.departureTime
        upsert(date, arrival, departure)
    }

    fun addMonths(delta: Int) {
        val (y, m) = month.split("-").let { it[0].toInt() to it[1].toInt() }
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, 1, 0, 0, 0)
        cal.add(Calendar.MONTH, delta)
        month = yyyyMm(cal)
    }

    suspend fun punchNow() {
        if (monthLocked) return
        val now = Calendar.getInstance()
        val hhmm = "${pad2(now.get(Calendar.HOUR_OF_DAY))}:${pad2(now.get(Calendar.MINUTE))}"
        val idx = rows.indexOfFirst { it.date == today }
        if (idx < 0) return
        val r = rows[idx]
        if (r.arrivalTime.isNullOrBlank()) {
            rows[idx] = r.copy(arrivalTime = hhmm)
            upsert(today, hhmm, r.departureTime)
            return
        }
        if (r.departureTime.isNullOrBlank()) {
            rows[idx] = r.copy(departureTime = hhmm)
            upsert(today, r.arrivalTime, hhmm)
        }
    }

    LaunchedEffect(month, refreshNonce) {
        loadMonth()
        flushQueueIfPossible()
    }

    LaunchedEffect(punchNonce) {
        if (punchNonce > 0) {
            punchNow()
        }
    }

    val monthText = monthLabelCs(month).uppercase()
    val monthStats = computeMonthStats(rows.map { AttendanceRowLike(it.date, it.arrivalTime, it.departureTime) }, employmentTemplate, cutoffMinutes)
    val workingFundHours = remember(month) {
        val (y, m) = month.split("-").let { it[0].toInt() to it[1].toInt() }
        workingDaysInMonthCs(y, m) * 8
    }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(monthText, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                Text(displayName ?: "—", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { addMonths(-1) }) { Text("←") }
                Button(onClick = { refreshNonce += 1 }) { Text("Obnovit") }
                Button(onClick = { punchNonce += 1 }) { Text("TEĎ") }
                OutlinedButton(onClick = { addMonths(+1) }) { Text("→") }
            }
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
        )

        if (monthLocked) {
            Card(modifier = Modifier.padding(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Měsíc uzavřen", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text("Docházka pro tento měsíc je uzavřena administrátorem (HTTP 423).")
                }
            }
        }
        if (!online) {
            Card(modifier = Modifier.padding(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Offline", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text("Nelze načíst data ze serveru. Změny se drží pouze v paměti a odešlou se po obnově připojení (pokud aplikace běží).")
                }
            }
        }
        if (queue.isNotEmpty()) {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Čekající změny: ${queue.size}", fontWeight = FontWeight.Bold)
                    Text("Neukládá se na disk. Zavřením aplikace se fronta ztratí.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isNotEmpty()) {
                item {
                    Card {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Den", fontWeight = FontWeight.Bold)
                            Text("Příchod", fontWeight = FontWeight.Bold)
                            Text("Odchod", fontWeight = FontWeight.Bold)
                            Text("Hodiny", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(rows) { r ->
                val calc = computeDayCalc(AttendanceRowLike(r.date, r.arrivalTime, r.departureTime), employmentTemplate, cutoffMinutes)
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${r.date.substring(8, 10)}.", fontWeight = FontWeight.Black)
                                Text(toDowLabelCs(r.date) + (calc.holidayName?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelMedium)
                                if (calc.breakLabel != null) {
                                    Text(calc.breakLabel, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(if (calc.workedMins != null) "${formatHours(calc.workedMins)} h" else "—", fontWeight = FontWeight.Black)
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeField(
                                label = "Příchod",
                                value = r.arrivalTime ?: "",
                                planned = r.plannedArrivalTime,
                                enabled = !monthLocked,
                                onCommit = { onChangeTime(r.date, "arrival", it) }
                            )
                            TimeField(
                                label = "Odchod",
                                value = r.departureTime ?: "",
                                planned = r.plannedDepartureTime,
                                enabled = !monthLocked,
                                onCommit = { onChangeTime(r.date, "departure", it) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("Souhrn", fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("ID entity: $instanceId")
                        Text("Název entity: ${displayName ?: "—"}")
                        Text("Součet hodin (${monthLabelCs(month)}): ${formatHours(monthStats.totalMins)} h")
                        Text("Víkend + svátky: ${formatHours(monthStats.weekendHolidayMins)} h")
                        Text("Odpolední (${afternoonCutoff ?: "17:00"}): ${formatHours(monthStats.afternoonMins)} h")
                        Text("Pracovní fond: $workingFundHours h")
                        Spacer(Modifier.height(8.dp))
                        Text("Docházka se ukládá pouze na serveru. Offline změny jsou dočasné a ztratí se při zavření aplikace.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

        // If you want an explicit onClick wiring, add a small state flag and toggle it from the button.
}

@Composable
private fun RowScope.TimeField(
    label: String,
    value: String,
    planned: String?,
    enabled: Boolean,
    onCommit: suspend (String) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    val ok = isValidTimeOrEmpty(local)

    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (!planned.isNullOrBlank()) Text("Plán: $planned", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = local,
            onValueChange = { local = it.take(5) },
            enabled = enabled,
            singleLine = true,
            isError = !ok,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("HH:MM") },
            modifier = Modifier.fillMaxWidth()
        )
        if (!ok) Text("Zadejte HH:MM (00:00–23:59) nebo nechte prázdné.", style = MaterialTheme.typography.labelSmall)
        LaunchedEffect(local) {
            delay(600)
            if (isValidTimeOrEmpty(local)) onCommit(local)
        }
    }
}
