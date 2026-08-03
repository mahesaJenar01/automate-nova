package com.nova.automate

import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nova.automate.service.NovaAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext

enum class AppScreen {
    Home,
    ProductList,
    ExtractedData,
    LaporanList,
    LaporanDetail
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
                val sharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isServiceEnabled = checkServiceEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                var currentScreen by remember { mutableStateOf(AppScreen.Home) }
                var extractedData by remember { mutableStateOf(emptyList<ProcessedItem>()) }
                var selectedReport by remember { mutableStateOf<LaporanReport?>(null) }
                
                var selectedDate by remember { mutableStateOf(sharedPreferences.getString("target_date", "Belum ada tanggal") ?: "Belum ada tanggal") }

                val calendar = Calendar.getInstance()
                val datePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val selectedCalendar = Calendar.getInstance()
                        selectedCalendar.set(year, month, dayOfMonth)
                        val format = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
                        val formattedDate = format.format(selectedCalendar.time)
                        
                        selectedDate = formattedDate
                        sharedPreferences.edit().putString("target_date", formattedDate).apply()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                var showAbsenceDialog by remember { mutableStateOf(false) }
                if (showAbsenceDialog) {
                    AlertDialog(
                        onDismissRequest = { showAbsenceDialog = false },
                        title = { Text("Peringatan Absen") },
                        text = { Text("Tanggal yang Anda pilih ($selectedDate) telah terdeteksi sebagai hari libur/absen di Nova App.\n\nJika Anda tetap ingin memasukkan data ini ke tanggal lain, silakan ganti tanggal yang dipilih terlebih dahulu.") },
                        confirmButton = {
                            TextButton(onClick = { 
                                showAbsenceDialog = false
                                datePickerDialog.show()
                            }) {
                                Text("Ganti Tanggal")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAbsenceDialog = false }) {
                                Text("Tutup")
                            }
                        }
                    )
                }

                when (currentScreen) {
                    AppScreen.Home -> MainScreen(
                        selectedDate = selectedDate,
                        datePickerDialog = datePickerDialog,
                        onNavigateToProducts = { currentScreen = AppScreen.ProductList },
                        onNavigateToLaporan = { currentScreen = AppScreen.LaporanList },
                        onDataExtracted = { data ->
                            extractedData = data
                            currentScreen = AppScreen.ExtractedData
                        }
                    )
                    AppScreen.ProductList -> ProductListScreen(onNavigateBack = { currentScreen = AppScreen.Home })
                    AppScreen.ExtractedData -> ExtractedDataScreen(
                        items = extractedData,
                        isServiceEnabled = isServiceEnabled,
                        onNavigateBack = { currentScreen = AppScreen.Home },
                        onRunAutomation = { itemsToRun ->
                            val absenceJson = sharedPreferences.getString("absence_dates", "[]") ?: "[]"
                            val isAbsence = try {
                                val array = org.json.JSONArray(absenceJson)
                                var found = false
                                for (i in 0 until array.length()) {
                                    if (array.getString(i) == selectedDate) {
                                        found = true
                                        break
                                    }
                                }
                                found
                            } catch (e: Exception) { false }

                            if (isAbsence) {
                                showAbsenceDialog = true
                            } else {
                                val jsonArray = org.json.JSONArray()
                                for (item in itemsToRun) {
                                    if (item.productCode != null) {
                                        val obj = org.json.JSONObject()
                                        obj.put("code", item.productCode)
                                        obj.put("qty", item.qty.toString())
                                        obj.put("name", item.originalName)
                                        jsonArray.put(obj)
                                    }
                                }
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.pti.nova")
                                if (launchIntent != null) {
                                    // Arm a single automation run. The accessibility service consumes
                                    // this flag as soon as it fires, so opening Nova by hand later
                                    // never re-runs the automation.
                                    sharedPreferences.edit()
                                        .putString("products_to_add", jsonArray.toString())
                                        .putBoolean("automation_requested", true)
                                        .putLong("automation_requested_at", System.currentTimeMillis())
                                        .apply()

                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(launchIntent)
                                } else {
                                    Toast.makeText(context, "App com.pti.nova not found!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onEnableService = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                    AppScreen.LaporanList -> LaporanListScreen(
                        onNavigateBack = { currentScreen = AppScreen.Home },
                        onNavigateToDetail = { report ->
                            selectedReport = report
                            currentScreen = AppScreen.LaporanDetail
                        }
                    )
                    AppScreen.LaporanDetail -> selectedReport?.let { report ->
                        LaporanDetailScreen(
                            report = report,
                            onNavigateBack = { currentScreen = AppScreen.LaporanList }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    selectedDate: String,
    datePickerDialog: DatePickerDialog,
    onNavigateToProducts: () -> Unit,
    onNavigateToLaporan: () -> Unit,
    onDataExtracted: (List<ProcessedItem>) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    val scope = rememberCoroutineScope()
    var showMismatchDialog by remember { mutableStateOf(false) }

    if (showMismatchDialog) {
        AlertDialog(
            onDismissRequest = { showMismatchDialog = false },
            title = { Text("Peringatan") },
            text = { Text("Tanggal files berbeda dengan tanggal yang dipilih.") },
            confirmButton = {
                TextButton(onClick = { showMismatchDialog = false }) {
                    Text("Okay")
                }
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val expectedDateStr = selectedDate.substringAfter(",").trim().lowercase()
                val fileName = getFileName(context, uri).lowercase()
                
                val dateParts = expectedDateStr.split(" ").filter { it.isNotBlank() }
                val monthMap = mapOf(
                    "januari" to listOf("01", "1", "jan"),
                    "februari" to listOf("02", "2", "feb"),
                    "maret" to listOf("03", "3", "mar"),
                    "april" to listOf("04", "4", "apr"),
                    "mei" to listOf("05", "5", "may"),
                    "juni" to listOf("06", "6", "jun", "june"),
                    "juli" to listOf("07", "7", "jul", "july"),
                    "agustus" to listOf("08", "8", "agu", "aug"),
                    "september" to listOf("09", "9", "sep"),
                    "oktober" to listOf("10", "okt", "oct"),
                    "november" to listOf("11", "nov"),
                    "desember" to listOf("12", "des", "dec")
                )

                val isMatch = if (selectedDate == "Belum ada tanggal") {
                    true
                } else {
                    val normalizedFileName = fileName.replace("_", " ").replace("-", " ")
                    dateParts.all { part ->
                        val alternates = monthMap[part]
                        if (alternates != null) {
                            normalizedFileName.contains(part) || alternates.any { normalizedFileName.contains(it) }
                        } else if (part.length == 2 && part.startsWith("0")) {
                            normalizedFileName.contains(part) || normalizedFileName.contains(part.substring(1))
                        } else {
                            normalizedFileName.contains(part)
                        }
                    }
                }

                if (!isMatch) {
                    showMismatchDialog = true
                } else {
                    scope.launch {
                        val db = ProductDatabaseManager.getProducts(context)
                        val result = withContext(Dispatchers.IO) {
                            ExcelProcessor.processExcel(context, uri, db)
                        }
                        onDataExtracted(result)
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Automate Nova Ready", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))

        Text("Target Tanggal: $selectedDate", style = MaterialTheme.typography.bodyLarge)
        
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { datePickerDialog.show() }) {
            Text("Pilih Tanggal")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Run button moved to ExtractedDataScreen

        Button(onClick = onNavigateToProducts) {
            Text("Kode Produk")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            filePickerLauncher.launch(
                arrayOf(
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
            )
        }) {
            Text("Upload File")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onNavigateToLaporan) {
            Text("Laporan")
        }
    }
}

@Composable
fun ProductListScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val products = remember { ProductDatabaseManager.getProducts(context) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNavigateBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Daftar Kode Produk", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(products.toList()) { (name, code) ->
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Kode: $code", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Divider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

private fun checkServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, NovaAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)

    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: ""
}