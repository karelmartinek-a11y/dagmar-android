package cz.hcasc.dagmarng.util

import cz.hcasc.dagmarng.net.Api
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

data class DayComputed(
    val workedMins: Int?,
    val breakMins: Int,
    val breakLabel: String?,
    val breakTooltip: String?,
    val afternoonMins: Int,
    val weekendHolidayMins: Int,
    val isWeekend: Boolean,
    val holidayName: String?,
    val isWeekendOrHoliday: Boolean
)

data class MonthStats(
    val totalMins: Int,
    val breakMins: Int,
    val afternoonMins: Int,
    val weekendHolidayMins: Int
)

private fun pad2(n: Int) = n.toString().padStart(2, '0')

private fun parseTimeToMinutes(hhmm: String?): Int? {
    if (hhmm.isNullOrBlank()) return null
    val m = Regex("^([0-1]?\\d|2[0-3]):([0-5]\\d)$").find(hhmm) ?: return null
    val h = m.groupValues[1].toInt()
    val mm = m.groupValues[2].toInt()
    return h * 60 + mm
}

fun parseCutoffToMinutes(value: String?, fallback: String = "17:00"): Int {
    val p = parseTimeToMinutes(value)
    if (p != null) return p
    return parseTimeToMinutes(fallback) ?: (17 * 60)
}

private fun minutesToHHMM(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return "${pad2(h)}:${pad2(m)}"
}

private fun isoParts(dateIso: String): Triple<Int, Int, Int>? {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(dateIso) ?: return null
    val y = m.groupValues[1].toInt()
    val mo = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    return Triple(y, mo, d)
}

fun isWeekendDate(dateIso: String): Boolean {
    val p = isoParts(dateIso) ?: return false
    val cal = Calendar.getInstance()
    cal.set(p.first, p.second - 1, p.third, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    return dow == Calendar.SUNDAY || dow == Calendar.SATURDAY
}

private fun addDays(cal: Calendar, delta: Int): Calendar {
    val out = cal.clone() as Calendar
    out.add(Calendar.DAY_OF_MONTH, delta)
    return out
}

private fun easterSunday(year: Int): Calendar {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, day, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

private val holidayCache = HashMap<Int, HashMap<String, String>>()

private fun toIso(cal: Calendar): String {
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "$y-${pad2(m)}-${pad2(d)}"
}

private fun holidaysForYearCZ(year: Int): HashMap<String, String> {
    holidayCache[year]?.let { return it }
    val map = HashMap<String, String>()
    val fixed = listOf(
        "01-01" to "Nový rok / Den obnovy samostatného českého státu",
        "05-01" to "Svátek práce",
        "05-08" to "Den vítězství",
        "07-05" to "Cyril a Metoděj",
        "07-06" to "Upálení mistra Jana Husa",
        "09-28" to "Den české státnosti",
        "10-28" to "Vznik samostatného československého státu",
        "11-17" to "Den boje za svobodu a demokracii",
        "12-24" to "Štědrý den",
        "12-25" to "1. svátek vánoční",
        "12-26" to "2. svátek vánoční"
    )
    for ((mmdd, name) in fixed) map["$year-$mmdd"] = name
    val easter = easterSunday(year)
    map[toIso(addDays(easter, -2))] = "Velký pátek"
    map[toIso(addDays(easter, +1))] = "Velikonoční pondělí"
    holidayCache[year] = map
    return map
}

fun getCzechHolidayName(dateIso: String): String? {
    val p = isoParts(dateIso) ?: return null
    val map = holidaysForYearCZ(p.first)
    return map[dateIso]
}

fun workingDaysInMonthCs(year: Int, month1: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(year, month1 - 1, 1, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    var working = 0
    for (d in 1..dim) {
        cal.set(year, month1 - 1, d, 0, 0, 0)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) continue
        val iso = "$year-${pad2(month1)}-${pad2(d)}"
        if (getCzechHolidayName(iso) != null) continue
        working += 1
    }
    return working
}

private data class BreakWindow(val start: Int, val end: Int)

private fun computeHppBreaks(startMin: Int, endMin: Int): List<BreakWindow> {
    val dur = endMin - startMin
    val breaks = ArrayList<BreakWindow>()
    if (dur >= 6 * 60 + 30) breaks.add(BreakWindow(startMin + 6 * 60, startMin + 6 * 60 + 30))
    if (dur >= 12 * 60 + 30) breaks.add(BreakWindow(startMin + 12 * 60, startMin + 12 * 60 + 30))
    return breaks
}

private fun segmentsMinusBreaks(startMin: Int, endMin: Int, breaks: List<BreakWindow>): List<Pair<Int, Int>> {
    if (breaks.isEmpty()) return listOf(startMin to endMin)
    val out = ArrayList<Pair<Int, Int>>()
    var cur = startMin
    for (b in breaks) {
        if (b.start > cur) out.add(cur to b.start)
        cur = max(cur, b.end)
    }
    if (cur < endMin) out.add(cur to endMin)
    return out.filter { it.second > it.first }
}

private fun overlapMinutes(a0: Int, a1: Int, b0: Int, b1: Int): Int {
    val s = max(a0, b0)
    val e = min(a1, b1)
    return max(0, e - s)
}

private fun breakLabelFromMinutes(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return "−$h:${pad2(m)} pauza"
}

private fun breakTooltipFromWindows(windows: List<BreakWindow>): String {
    if (windows.isEmpty()) return ""
    val parts = windows.map { "${minutesToHHMM(it.start)}–${minutesToHHMM(it.end)}" }
    val total = windows.size * 30
    val prefix = if (windows.size == 1) "Pauza" else "Pauzy"
    return "$prefix ${breakLabelFromMinutes(total).replace("−", "")} (${parts.joinToString(", ")})"
}

data class AttendanceRowLike(val date: String, val arrivalTime: String?, val departureTime: String?)

fun computeDayCalc(row: AttendanceRowLike, template: Api.EmploymentTemplate, cutoffMinutes: Int): DayComputed {
    val isWeekend = isWeekendDate(row.date)
    val holidayName = getCzechHolidayName(row.date)
    val isWeekendOrHoliday = isWeekend || holidayName != null

    val a = parseTimeToMinutes(row.arrivalTime)
    val d = parseTimeToMinutes(row.departureTime)
    if (a == null || d == null || d <= a) {
        return DayComputed(null, 0, null, null, 0, 0, isWeekend, holidayName, isWeekendOrHoliday)
    }

    if (template != Api.EmploymentTemplate.HPP) {
        return DayComputed(d - a, 0, null, null, 0, 0, isWeekend, holidayName, isWeekendOrHoliday)
    }

    val breaks = computeHppBreaks(a, d)
    val segments = segmentsMinusBreaks(a, d, breaks)
    val workedMins = segments.sumOf { it.second - it.first }
    val afternoonMins = segments.sumOf { overlapMinutes(it.first, it.second, cutoffMinutes, 24 * 60) }
    val weekendHolidayMins = if (isWeekendOrHoliday) workedMins else 0
    val breakMins = breaks.size * 30

    return DayComputed(
        workedMins = workedMins,
        breakMins = breakMins,
        breakLabel = if (breakMins > 0) breakLabelFromMinutes(breakMins) else null,
        breakTooltip = if (breaks.isNotEmpty()) breakTooltipFromWindows(breaks) else null,
        afternoonMins = afternoonMins,
        weekendHolidayMins = weekendHolidayMins,
        isWeekend = isWeekend,
        holidayName = holidayName,
        isWeekendOrHoliday = isWeekendOrHoliday
    )
}

fun computeMonthStats(rows: List<AttendanceRowLike>, template: Api.EmploymentTemplate, cutoffMinutes: Int): MonthStats {
    var totalMins = 0
    var breakMins = 0
    var afternoonMins = 0
    var weekendHolidayMins = 0
    for (r in rows) {
        val c = computeDayCalc(r, template, cutoffMinutes)
        c.workedMins?.let { totalMins += it }
        breakMins += c.breakMins
        afternoonMins += c.afternoonMins
        weekendHolidayMins += c.weekendHolidayMins
    }
    return if (template != Api.EmploymentTemplate.HPP) MonthStats(totalMins, 0, 0, 0)
    else MonthStats(totalMins, breakMins, afternoonMins, weekendHolidayMins)
}
