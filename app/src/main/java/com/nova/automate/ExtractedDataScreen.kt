package com.nova.automate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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

    val missingCount = mutableItems.count { it.productCode == null || it.productCode.isBlank() }
    val totalQty = mutableItems.sumOf { it.qty }

    if (missingCodeError != null) {
        AlertDialog(
            onDismissRequest = { missingCodeError = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Peringatan") },
            text = { Text(missingCodeError!!) },
            confirmButton = {
                TextButton(onClick = { missingCodeError = null }) { Text("Okay") }
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = selectedItemForCode?.originalName ?: "",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        label = { Text("Kode Produk") },
                        singleLine = true
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

    NovaScreen {
        NovaHeader(
            title = "Data Uploaded",
            subtitle = "${mutableItems.size} produk, total qty $totalQty",
            onBack = onNavigateBack
        )

        if (missingCount > 0) {
            NovaCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                borderColor = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$missingCount produk belum punya kode. Ketuk kartunya untuk mengisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mutableItems) { item ->
                val missing = item.productCode == null || item.productCode.isBlank()
                val openCodeDialog: () -> Unit = {
                    selectedItemForCode = item
                    inputCode = ""
                }

                NovaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (missing) openCodeDialog else null,
                    containerColor = if (missing) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surface,
                    borderColor = if (missing) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outlineVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.originalName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (missing) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (missing) {
                                Pill(
                                    text = "Ketuk untuk isi kode",
                                    container = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            } else {
                                Pill(
                                    text = item.productCode ?: "",
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "QTY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (missing) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = item.qty.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (missing) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        BottomBar {
            NovaCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (isDateOverridden) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                borderColor = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isDateOverridden) {
                        SectionLabel("Data tanggal")
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dataDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    SectionLabel("Input ke tanggal")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = runTargetDate,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onPickOtherDate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Input in Other Date")
                }
                if (isDateOverridden) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onResetDate) {
                        Text("Reset")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isServiceEnabled) {
                Button(
                    onClick = {
                        val missingItem = mutableItems.find { it.productCode == null || it.productCode.isBlank() }
                        if (missingItem != null) {
                            missingCodeError = "Kode produk untuk ${missingItem.originalName} harus di isi terlebih dahulu sebelum melanjutkan."
                        } else {
                            onRunAutomation(mutableItems)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Run Automation", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onEnableService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Aktifkan Accessibility Service", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
