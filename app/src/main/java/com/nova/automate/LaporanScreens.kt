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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.util.Locale

data class LaporanItem(
    val code: String,
    val name: String,
    val qty: String,
    val price: Int,
    val totalPrice: Int
)

data class LaporanReport(
    val date: String,
    val totalQty: Int,
    val totalPrice: Int,
    val items: List<LaporanItem>
)

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

fun formatRupiah(number: Int): String {
    val localeID = Locale("in", "ID")
    val formatRupiah = NumberFormat.getCurrencyInstance(localeID)
    return formatRupiah.format(number.toLong()).replace("Rp", "Rp ")
}

@Composable
fun LaporanListScreen(onNavigateBack: () -> Unit, onNavigateToDetail: (LaporanReport) -> Unit) {
    val context = LocalContext.current
    val reports = remember { getLaporanData(context) }
    var currentPage by remember { mutableStateOf(0) }
    val itemsPerPage = 10

    val totalPages = (reports.size + itemsPerPage - 1) / itemsPerPage
    val currentReports = reports.drop(currentPage * itemsPerPage).take(itemsPerPage)

    NovaScreen {
        NovaHeader(
            title = "Laporan",
            subtitle = "${reports.size} laporan tersimpan",
            onBack = onNavigateBack
        )

        if (reports.isEmpty()) {
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
                items(currentReports) { report ->
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

            if (totalPages > 1) {
                BottomBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = null
                            )
                            Text("Sebelumnya")
                        }
                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { if (currentPage < totalPages - 1) currentPage++ },
                            enabled = currentPage < totalPages - 1
                        ) {
                            Text("Berikutnya")
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
                    KeyValueRow("Total kuantitas", report.totalQty.toString())
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Total harga", formatRupiah(report.totalPrice), emphasis = true)
                }
            }
        }
    }
}
