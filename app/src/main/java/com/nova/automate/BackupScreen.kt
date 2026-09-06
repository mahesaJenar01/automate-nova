package com.nova.automate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moves the whole app state on and off the phone as one JSON file: product
 * codes, every laporan, and the selected target date.
 *
 * @param onDataChanged fired after an import so the caller can re-read anything
 *   it is holding in memory (the home screen's target date, for one).
 */
@Composable
fun BackupScreen(onNavigateBack: () -> Unit, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Re-read on every entry, and again after an import, so the counts always
    // match what is actually stored.
    var productCount by remember { mutableStateOf(ProductDatabaseManager.getProducts(context).size) }
    var laporanCount by remember { mutableStateOf(getLaporanData(context).size) }

    var pendingImport by remember { mutableStateOf<BackupManager.BackupContent?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                busy = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            BackupManager.exportTo(context, uri)
                        }
                        successMessage = "Data berhasil diekspor.\n\n" +
                            "$productCount kode produk\n" +
                            "$laporanCount laporan\n" +
                            "Ukuran file ${formatFileSize(bytes)}"
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Gagal menulis file cadangan."
                    } finally {
                        busy = false
                    }
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                busy = true
                scope.launch {
                    try {
                        // Read and validate first — nothing is written until the
                        // user picks a mode in the dialog below.
                        pendingImport = withContext(Dispatchers.IO) {
                            BackupManager.readBackup(context, uri)
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "File cadangan tidak bisa dibaca."
                    } finally {
                        busy = false
                    }
                }
            }
        }
    )

    fun runImport(mode: BackupManager.ImportMode) {
        val content = pendingImport ?: return
        pendingImport = null
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.applyBackup(context, content, mode)
                }
                productCount = ProductDatabaseManager.getProducts(context).size
                laporanCount = getLaporanData(context).size
                onDataChanged()
                successMessage = buildString {
                    append(
                        if (mode == BackupManager.ImportMode.REPLACE) "Data berhasil diganti.\n\n"
                        else "Data berhasil digabung.\n\n"
                    )
                    append("${result.productsAdded} kode produk ditambahkan\n")
                    append("${result.laporanAdded} laporan ditambahkan")
                    if (result.targetDateApplied) append("\nTarget tanggal ikut dipulihkan")
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Gagal memulihkan data."
            } finally {
                busy = false
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Gagal") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("Okay") }
            }
        )
    }

    if (successMessage != null) {
        AlertDialog(
            onDismissRequest = { successMessage = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text("Berhasil") },
            text = { Text(successMessage!!) },
            confirmButton = {
                TextButton(onClick = { successMessage = null }) { Text("Okay") }
            }
        )
    }

    pendingImport?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Impor Data") },
            text = {
                Column {
                    Text("File ini berisi:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• ${content.productCount} kode produk")
                    Text("• ${content.laporanCount} laporan")
                    if (content.exportedAt != null) {
                        Text("• Dibuat ${content.exportedAt}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { runImport(BackupManager.ImportMode.MERGE) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gabung")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Data yang sudah ada tetap dipertahankan, hanya yang belum ada yang ditambahkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { runImport(BackupManager.ImportMode.REPLACE) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Ganti Semua")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Semua data di HP ini dihapus dan diganti dengan isi file. Tidak bisa dibatalkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Batal") }
            }
        )
    }

    NovaScreen {
        NovaHeader(
            title = "Cadangan Data",
            subtitle = "Ekspor & impor semua data aplikasi",
            onBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            NovaCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                borderColor = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel("Isi cadangan")
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyValueRow("Kode produk", "$productCount kode")
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Laporan", "$laporanCount laporan")
                    Spacer(modifier = Modifier.height(6.dp))
                    KeyValueRow("Target tanggal", "Ikut disimpan")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Ekspor ke File", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    // Some file managers hand JSON back as octet-stream or plain
                    // text, so the picker stays open to any file.
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Impor dari File", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(20.dp))

            NovaCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    IconBadge(
                        icon = Icons.Default.Info,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Cadangan disimpan sebagai satu file JSON. Simpan di Google Drive " +
                                "atau kirim ke HP lain, lalu pakai Impor untuk memulihkannya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Laporan yang sama tidak akan tersimpan dua kali walaupun file " +
                                "yang sama diimpor berulang kali.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatFileSize(bytes: Int): String {
    return if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
}
