package com.nova.automate

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Daftar Laporan", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        
        if (reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada laporan.")
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(currentReports) { report ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDetail(report) }
                            .padding(16.dp)
                    ) {
                        Text(text = "Laporan ${report.date}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Total Item: ${report.totalQty} | Total Harga: ${formatRupiah(report.totalPrice)}", 
                             style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Divider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            
            if (totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Text("Previous")
                    }
                    Text("Page ${currentPage + 1} of $totalPages")
                    Button(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Composable
fun LaporanDetailScreen(report: LaporanReport, onNavigateBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Laporan ${report.date}", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        
        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
            items(report.items) { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Qty: ${item.qty} x ${formatRupiah(item.price)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Text(text = formatRupiah(item.totalPrice), style = MaterialTheme.typography.bodyLarge)
                    }
                    Divider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ringkasan", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Kuantitas:")
                    Text(text = report.totalQty.toString())
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Harga:")
                    Text(text = formatRupiah(report.totalPrice), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
