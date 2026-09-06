package com.nova.automate

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LaporanItem(
    val code: String,
    val name: String,
    val qty: String,
    val price: Int,
    val totalPrice: Int
)

data class LaporanReport(
    /** The day the uploaded data came from. */
    val date: String,
    /** The day the data was actually written into Nova. Differs when "Input in Other Date" was used. */
    val inputDate: String,
    val totalQty: Int,
    val totalPrice: Int,
    val items: List<LaporanItem>
)

/**
 * One Monday-to-Sunday page of the Laporan list, with the week's totals already
 * summed. [start] is null for the bucket holding reports whose date can't be read.
 */
data class LaporanWeek(
    val start: Date?,
    val end: Date?,
    val label: String,
    val reports: List<LaporanReport>
) {
    val totalQty: Int = reports.sumOf { it.totalQty }
    val totalPrice: Int = reports.sumOf { it.totalPrice }
}

fun getLaporanData(context: Context): List<LaporanReport> {
    val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("laporan_data", "[]") ?: "[]"
    val reports = mutableListOf<LaporanReport>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val itemsArray = obj.getJSONArray("items")
            val itemsList = mutableListOf<LaporanItem>()
            for (j in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(j)
                itemsList.add(
                    LaporanItem(
                        code = itemObj.getString("code"),
                        name = itemObj.optString("name", itemObj.getString("code")),
                        qty = itemObj.getString("qty"),
                        price = itemObj.getInt("price"),
                        totalPrice = itemObj.getInt("totalPrice")
                    )
                )
            }
            reports.add(
                LaporanReport(
                    date = obj.getString("date"),
                    // Reports saved before the two dates were split only carry "date".
                    inputDate = obj.optString("inputDate", obj.getString("date")),
                    totalQty = obj.getInt("totalQty"),
                    totalPrice = obj.getInt("totalPrice"),
                    items = itemsList
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return reports.reversed() // Show newest first
}

/** "Jumat, 29 Agustus 2026" -> "29 Agustus 2026", so secondary lines stay short. */
fun shortDate(date: String): String {
    return if (date.contains(",")) date.substringAfter(",").trim() else date
}

/** Reads the "EEEE, dd MMMM yyyy" strings the app stores, weekday optional. */
fun parseIndonesianDate(dateString: String?): Date? {
    if (dateString.isNullOrBlank()) return null
    val localeID = Locale("id", "ID")
    return try {
        SimpleDateFormat("EEEE, dd MMMM yyyy", localeID).parse(dateString)
    } catch (e: Exception) {
        try {
            SimpleDateFormat("dd MMMM yyyy", localeID).parse(shortDate(dateString))
        } catch (e2: Exception) {
            null
        }
    }
}

fun formatRupiah(number: Int): String {
    val localeID = Locale("in", "ID")
    val formatRupiah = NumberFormat.getCurrencyInstance(localeID)
    return formatRupiah.format(number.toLong()).replace("Rp", "Rp ")
}

/** One decimal at most, comma separator, no trailing ",0". */
private fun trimDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString()
    else String.format(Locale("id", "ID"), "%.1f", rounded)
}

/**
 * "Rp 1,2 jt" / "Rp 850 rb" — for the stat tiles, where the full number would
 * be cut off. Use [formatRupiah] anywhere the exact figure matters.
 */
fun formatRupiahShort(value: Int): String {
    val abs = Math.abs(value.toLong())
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000L -> "${sign}Rp ${trimDecimal(abs / 1_000_000_000.0)} M"
        abs >= 1_000_000L -> "${sign}Rp ${trimDecimal(abs / 1_000_000.0)} jt"
        abs >= 1_000L -> "${sign}Rp ${trimDecimal(abs / 1_000.0)} rb"
        else -> "${sign}Rp $abs"
    }
}

/** Local midnight on the day [date] falls in. */
private fun midnight(date: Date): Long {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.clear(Calendar.MINUTE)
    cal.clear(Calendar.SECOND)
    cal.clear(Calendar.MILLISECOND)
    return cal.timeInMillis
}

/**
 * "Hari ini" / "Kemarin" / "3 hari lalu" for a stored date string, so the target
 * date can be placed in time at a glance. Null when the string can't be read.
 */
fun relativeDayLabel(dateString: String, today: Date = Date()): String? {
    val target = parseIndonesianDate(dateString) ?: return null
    val days = Math.round((midnight(target) - midnight(today)) / 86_400_000.0)
    return when {
        days == 0L -> "Hari ini"
        days == -1L -> "Kemarin"
        days == 1L -> "Besok"
        days < 0L -> "${-days} hari lalu"
        else -> "$days hari lagi"
    }
}

/** Totals for one Monday-to-Sunday week, whether or not anything landed in it. */
data class WeekTotal(
    val start: Date,
    /** Short axis label, e.g. "1/9". */
    val label: String,
    /** The full range, e.g. "1 – 7 September 2026". */
    val rangeLabel: String,
    val reportCount: Int,
    val totalQty: Int,
    val totalPrice: Int
)

/**
 * The last [count] weeks, ending with the one [today] falls in, oldest first.
 * Weeks with nothing in them are kept: a gap between two busy weeks is part of
 * what the chart is showing, and dropping it would compress the timeline.
 */
fun recentWeekTotals(
    reports: List<LaporanReport>,
    count: Int = 6,
    today: Date = Date()
): List<WeekTotal> {
    val buckets = mutableMapOf<Long, MutableList<LaporanReport>>()
    for (report in reports) {
        val parsed = parseIndonesianDate(report.date) ?: continue
        buckets.getOrPut(startOfWeek(parsed).time) { mutableListOf() }.add(report)
    }

    val dayMonth = SimpleDateFormat("d/M", Locale("id", "ID"))
    val thisWeek = startOfWeek(today)
    return (count - 1 downTo 0).map { weeksBack ->
        // Re-normalise: an exact multiple of 7 days can drift off Monday if the
        // clock ever shifts under us.
        val start = startOfWeek(plusDays(thisWeek, -7 * weeksBack))
        val inWeek = buckets[start.time].orEmpty()
        WeekTotal(
            start = start,
            label = dayMonth.format(start),
            rangeLabel = weekLabel(start, plusDays(start, 6)),
            reportCount = inWeek.size,
            totalQty = inWeek.sumOf { it.totalQty },
            totalPrice = inWeek.sumOf { it.totalPrice }
        )
    }
}

/** Midnight on the Monday of the week [date] falls in. */
private fun startOfWeek(date: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.clear(Calendar.MINUTE)
    cal.clear(Calendar.SECOND)
    cal.clear(Calendar.MILLISECOND)
    // Calendar counts SUNDAY as 1, so Sunday sits six days after its Monday.
    var offset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
    if (offset < 0) offset += 7
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    return cal.time
}

private fun plusDays(date: Date, days: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.add(Calendar.DAY_OF_MONTH, days)
    return cal.time
}

/**
 * "31 Agustus – 6 September 2026". Only the parts that actually change between
 * the two ends are repeated, so the common case stays short.
 */
fun weekLabel(start: Date, end: Date): String {
    val localeID = Locale("id", "ID")
    val dayOnly = SimpleDateFormat("d", localeID)
    val dayMonth = SimpleDateFormat("d MMMM", localeID)
    val full = SimpleDateFormat("d MMMM yyyy", localeID)
    val year = SimpleDateFormat("yyyy", localeID)
    val month = SimpleDateFormat("MMMM", localeID)

    val head = when {
        year.format(start) != year.format(end) -> full.format(start)
        month.format(start) != month.format(end) -> dayMonth.format(start)
        else -> dayOnly.format(start)
    }
    return "$head – ${full.format(end)}"
}

/** Buckets reports into Monday-to-Sunday weeks, newest week first. */
fun groupIntoWeeks(reports: List<LaporanReport>): List<LaporanWeek> {
    val dated = mutableMapOf<Long, MutableList<LaporanReport>>()
    val undated = mutableListOf<LaporanReport>()

    for (report in reports) {
        val parsed = parseIndonesianDate(report.date)
        if (parsed == null) {
            undated.add(report)
        } else {
            dated.getOrPut(startOfWeek(parsed).time) { mutableListOf() }.add(report)
        }
    }

    val weeks = dated.entries
        .sortedByDescending { it.key }
        .map { (startMillis, weekReports) ->
            val start = Date(startMillis)
            val end = plusDays(start, 6)
            LaporanWeek(
                start = start,
                end = end,
                label = weekLabel(start, end),
                reports = weekReports.sortedByDescending {
                    parseIndonesianDate(it.date)?.time ?: 0L
                }
            )
        }
        .toMutableList()

    if (undated.isNotEmpty()) {
        weeks.add(
            LaporanWeek(
                start = null,
                end = null,
                label = "Tanpa tanggal",
                reports = undated
            )
        )
    }
    return weeks
}

@Composable
fun LaporanListScreen(onNavigateBack: () -> Unit, onNavigateToDetail: (LaporanReport) -> Unit) {
    val context = LocalContext.current
    val reports = remember { getLaporanData(context) }
    val weeks = remember(reports) { groupIntoWeeks(reports) }
    // Page 0 is the most recent week; paging back walks into older weeks.
    var currentPage by remember { mutableStateOf(0) }
    val week = weeks.getOrNull(currentPage)

    NovaScreen {
        NovaHeader(
            title = "Laporan",
            subtitle = if (weeks.isEmpty()) "${reports.size} laporan tersimpan"
            else "${reports.size} laporan • ${weeks.size} minggu",
            onBack = onNavigateBack
        )

        if (week == null) {
            EmptyState(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.List,
                title = "Belum ada laporan",
                message = "Laporan tersimpan otomatis setiap kali automation selesai mengirim sellout."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Keyed on the week so paging replays the counts and the bars
                    // instead of silently swapping the numbers underneath.
                    key(week.label) { WeekSummaryCard(week) }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(week.reports) { report ->
                    NovaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToDetail(report) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(
                                icon = Icons.Default.CheckCircle,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.date,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (report.inputDate != report.date) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Diinput ke ${shortDate(report.inputDate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Pill(text = "${report.totalQty} item")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatRupiah(report.totalPrice),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            if (weeks.size > 1) {
                BottomBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Older weeks sit further down the list, newer ones above.
                        TextButton(
                            onClick = { if (currentPage < weeks.size - 1) currentPage++ },
                            enabled = currentPage < weeks.size - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = null
                            )
                            Text("Minggu Lalu")
                        }
                        Text(
                            text = "${currentPage + 1} / ${weeks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Text("Minggu Berikutnya")
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_LABELS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

/**
 * The week's value split across its seven days, for the summary chart. Days
 * that carry a report are highlighted, so the shape of the week reads at a
 * glance. Empty for the undated bucket, which has no span to spread over.
 */
private fun dailyBars(week: LaporanWeek): List<ChartBar> {
    val start = week.start ?: return emptyList()
    val totals = LongArray(7)
    for (report in week.reports) {
        val date = parseIndonesianDate(report.date) ?: continue
        val index = Math.round((midnight(date) - midnight(start)) / 86_400_000.0).toInt()
        if (index in 0..6) totals[index] += report.totalPrice.toLong()
    }
    return WEEKDAY_LABELS.mapIndexed { index, label ->
        ChartBar(label = label, value = totals[index], highlighted = totals[index] > 0L)
    }
}

/** Header card of a week page: the date range plus everything in it, summed. */
@Composable
private fun WeekSummaryCard(week: LaporanWeek) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val bars = remember(week) { dailyBars(week) }

    NovaCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Default.DateRange,
                    container = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Minggu ini")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = week.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Laporan",
                    modifier = Modifier.weight(1f),
                    contentColor = onContainer,
                    labelColor = onContainer.copy(alpha = 0.7f)
                ) {
                    AnimatedCount(
                        value = week.reports.size,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                StatTile(
                    label = "Kuantitas",
                    modifier = Modifier.weight(1f),
                    contentColor = onContainer,
                    labelColor = onContainer.copy(alpha = 0.7f)
                ) {
                    AnimatedCount(
                        value = week.totalQty,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                StatTile(
                    label = "Nilai",
                    modifier = Modifier.weight(1f),
                    contentColor = onContainer,
                    labelColor = onContainer.copy(alpha = 0.7f)
                ) {
                    AnimatedCount(
                        value = week.totalPrice,
                        style = MaterialTheme.typography.titleLarge,
                        format = { formatRupiahShort(it) }
                    )
                }
            }

            if (bars.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                NovaBarChart(
                    bars = bars,
                    barColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    height = 64.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            KeyValueRow("Total harga", formatRupiah(week.totalPrice), emphasis = true)
        }
    }
}

@Composable
fun LaporanDetailScreen(report: LaporanReport, onNavigateBack: () -> Unit) {
    NovaScreen {
        NovaHeader(
            title = "Detail Laporan",
            subtitle = report.date,
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(report.items) { item ->
                NovaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.qty} x ${formatRupiah(item.price)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRupiah(item.totalPrice),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }

        BottomBar {
            NovaCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel("Ringkasan")
                    Spacer(modifier = Modifier.height(10.dp))
                    if (report.inputDate != report.date) {
                        KeyValueRow("Tanggal data", shortDate(report.date))
                        Spacer(modifier = Modifier.height(6.dp))
                        KeyValueRow("Diinput ke tanggal", shortDate(report.inputDate))
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    KeyValueRow("Total kuantitas", report.totalQty.toString())
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Total harga", formatRupiah(report.totalPrice), emphasis = true)
                }
            }
        }
    }
}
