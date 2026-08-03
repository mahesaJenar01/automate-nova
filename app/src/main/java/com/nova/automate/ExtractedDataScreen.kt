package com.nova.automate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.Arrangement

@Composable
fun ExtractedDataScreen(
    items: List<ProcessedItem>,
    isServiceEnabled: Boolean,
    dataDate: String,
    runTargetDate: String,
    isDateOverridden: Boolean,
    onPickOtherDate: () -> Unit,
    onResetDate: () -> Unit,
    onNavigateBack: () -> Unit,
    onRunAutomation: (List<ProcessedItem>) -> Unit,
    onEnableService: () -> Unit
) {
    var mutableItems by remember { mutableStateOf(items) }
    var selectedItemForCode by remember { mutableStateOf<ProcessedItem?>(null) }
    var missingCodeError by remember { mutableStateOf<String?>(null) }
    var inputCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (missingCodeError != null) {
        AlertDialog(
            onDismissRequest = { missingCodeError = null },
            title = { Text("Peringatan") },
            text = { Text(missingCodeError!!) },
            confirmButton = {
                Button(onClick = { missingCodeError = null }) { Text("Okay") }
            }
        )
    }

    if (selectedItemForCode != null) {
        AlertDialog(
            onDismissRequest = { selectedItemForCode = null },
            title = { Text("Kode Tidak Ditemukan") },
            text = {
                Column {
                    Text("Kode produk tidak ditemukan. Tolong cek dan masukan kodenya secara manual.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Produk: ${selectedItemForCode?.originalName}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        label = { Text("Kode Produk") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val codeToSave = inputCode.trim()
                    if (codeToSave.isNotEmpty() && selectedItemForCode != null) {
                        ProductDatabaseManager.saveProduct(context, selectedItemForCode!!.originalName, codeToSave)
                        
                        val updatedList = mutableItems.map { 
                            if (it.normalizedName == selectedItemForCode!!.normalizedName) {
                                it.copy(productCode = codeToSave)
                            } else {
                                it
                            }
                        }.sortedWith(compareBy<ProcessedItem> { it.productCode != null }.thenByDescending { it.qty })
                        
                        mutableItems = updatedList
                        selectedItemForCode = null
                        inputCode = ""
                    }
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForCode = null }) {
                    Text("Batal")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Data Uploaded", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(mutableItems) { item ->
                // Yellow background if productCode is missing
                val bgColor = if (item.productCode == null) Color.Yellow else Color.Transparent
                val textColor = if (item.productCode == null) Color.Black else MaterialTheme.colorScheme.onSurface
                val secondaryColor = if (item.productCode == null) Color.DarkGray else Color.Gray
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .then(if (item.productCode == null) Modifier.clickable {
                            selectedItemForCode = item
                            inputCode = ""
                        } else Modifier)
                        .padding(16.dp)
                ) {
                    Text(
                        text = item.originalName, 
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kode: ${item.productCode ?: "TIDAK DITEMUKAN"}", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = secondaryColor
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Qty: ${item.qty}", 
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor
                        )
                    }
                }
                Divider()
            }
        }
        Divider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Data tanggal: $dataDate",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = "Input ke tanggal: $runTargetDate",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPickOtherDate) {
                    Text("Input in Other Date")
                }
                if (isDateOverridden) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onResetDate) {
                        Text("Reset")
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (isServiceEnabled) {
                Button(onClick = { 
                    val missingItem = mutableItems.find { it.productCode == null || it.productCode.isBlank() }
                    if (missingItem != null) {
                        missingCodeError = "Kode produk untuk ${missingItem.originalName} harus di isi terlebih dahulu sebelum melanjutkan."
                    } else {
                        onRunAutomation(mutableItems) 
                    }
                }) {
                    Text("Run Automation")
                }
            } else {
                Button(onClick = onEnableService) {
                    Text("Enable Accessibility Service to Run")
                }
            }
        }
    }
}
