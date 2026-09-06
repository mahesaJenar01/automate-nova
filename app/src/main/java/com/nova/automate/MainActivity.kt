package com.nova.automate

import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nova.automate.service.NovaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AppScreen {
    Home,
    ProductList,
    ExtractedData,
    LaporanList,
    LaporanDetail,
    Backup
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaTheme {
                val context = LocalContext.current
                var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
                val sharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                var runMessage by remember { mutableStateOf<String?>(null) }
                // Bumped on every resume. The home summary is keyed on it so a
                // laporan saved during an automation run shows up on the way back.
                var dataRevision by remember { mutableStateOf(0) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isServiceEnabled = checkServiceEnabled(context)
                            dataRevision++

                            // The workflow leaves a note here when it stops before submitting.
                            val message = sharedPreferences.getString("last_run_message", null)
                            if (message != null) {
                                runMessage = message
                                sharedPreferences.edit().remove("last_run_message").apply()
                            }
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

                // The day the automation writes into. Null means "same day as the uploaded data".
                var overrideDate by remember { mutableStateOf<String?>(null) }
                val runTargetDate = overrideDate ?: selectedDate

                val runTargetCalendar = Calendar.getInstance()
                parseIndonesianDate(runTargetDate)?.let { runTargetCalendar.time = it }
                val otherDatePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val pickedCalendar = Calendar.getInstance()
                        pickedCalendar.set(year, month, dayOfMonth)
                        val format = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
                        overrideDate = format.format(pickedCalendar.time)
                    },
                    runTargetCalendar.get(Calendar.YEAR),
                    runTargetCalendar.get(Calendar.MONTH),
                    runTargetCalendar.get(Calendar.DAY_OF_MONTH)
                )

                if (runMessage != null) {
                    AlertDialog(
                        onDismissRequest = { runMessage = null },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = { Text("Automation Berhenti") },
                        text = { Text(runMessage!!) },
                        confirmButton = {
                            TextButton(onClick = { runMessage = null }) {
                                Text("Okay")
                            }
                        }
                    )
                }

                // Screens slide in from the side they sit on: deeper pages enter from
                // the right, going back sends them out the way they came.
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val direction = if (screenDepth(targetState) >= screenDepth(initialState)) 1 else -1
                        val slide = tween<IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
                        (slideInHorizontally(slide) { full -> direction * full / 5 } +
                            fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(slide) { full -> -direction * full / 5 } +
                                fadeOut(tween(160))) using SizeTransform(clip = false)
                    },
                    label = "screen"
                ) { screen ->
                when (screen) {
                    AppScreen.Home -> MainScreen(
                        selectedDate = selectedDate,
                        isServiceEnabled = isServiceEnabled,
                        datePickerDialog = datePickerDialog,
                        dataRevision = dataRevision,
                        onNavigateToProducts = { currentScreen = AppScreen.ProductList },
                        onNavigateToLaporan = { currentScreen = AppScreen.LaporanList },
                        onNavigateToBackup = { currentScreen = AppScreen.Backup },
                        onDataExtracted = { data ->
                            extractedData = data
                            overrideDate = null
                            currentScreen = AppScreen.ExtractedData
                        }
                    )
                    AppScreen.ProductList -> ProductListScreen(onNavigateBack = { currentScreen = AppScreen.Home })
                    AppScreen.ExtractedData -> ExtractedDataScreen(
                        items = extractedData,
                        isServiceEnabled = isServiceEnabled,
                        dataDate = selectedDate,
                        runTargetDate = runTargetDate,
                        isDateOverridden = overrideDate != null,
                        onPickOtherDate = { otherDatePickerDialog.show() },
                        onResetDate = { overrideDate = null },
                        onNavigateBack = { currentScreen = AppScreen.Home },
                        onRunAutomation = { itemsToRun ->
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
                                // run_target_date is the day the data is written into, which is not
                                // necessarily the day the data came from (run_data_date). The
                                // Laporan reports both so an override stays visible afterwards.
                                sharedPreferences.edit()
                                    .putString("products_to_add", jsonArray.toString())
                                    .putString("run_target_date", runTargetDate)
                                    .putString("run_data_date", selectedDate)
                                    .putBoolean("automation_requested", true)
                                    .putLong("automation_requested_at", System.currentTimeMillis())
                                    .apply()

                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(launchIntent)
                            } else {
                                Toast.makeText(context, "App com.pti.nova not found!", Toast.LENGTH_SHORT).show()
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
                    AppScreen.Backup -> BackupScreen(
                        onNavigateBack = { currentScreen = AppScreen.Home },
                        // An import can bring a target date with it, so pick the
                        // stored value back up instead of showing a stale one.
                        onDataChanged = {
                            selectedDate = sharedPreferences.getString("target_date", "Belum ada tanggal")
                                ?: "Belum ada tanggal"
                            dataRevision++
                        }
                    )
                }
                }
            }
        }
    }
}

/**
 * How far into the app a screen sits. Only the ordering matters — it decides
 * which way [AnimatedContent] slides between two screens.
 */
private fun screenDepth(screen: AppScreen): Int = when (screen) {
    AppScreen.Home -> 0
    AppScreen.ProductList, AppScreen.ExtractedData, AppScreen.LaporanList, AppScreen.Backup -> 1
    AppScreen.LaporanDetail -> 2
}

@Composable
fun MainScreen(
    selectedDate: String,
    isServiceEnabled: Boolean,
    datePickerDialog: DatePickerDialog,
    dataRevision: Int,
    onNavigateToProducts: () -> Unit,
    onNavigateToLaporan: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onDataExtracted: (List<ProcessedItem>) -> Unit
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var showMismatchDialog by remember { mutableStateOf(false) }

    if (showMismatchDialog) {
        AlertDialog(
            onDismissRequest = { showMismatchDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Tanggal Tidak Cocok") },
            text = { Text("Tanggal pada nama file berbeda dengan tanggal yang dipilih.") },
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

    val hasDate = selectedDate != "Belum ada tanggal"

    // Everything below the upload button is a read-only view of what is already
    // stored, re-read whenever the activity resumes.
    val reports = remember(dataRevision) { getLaporanData(context) }
    val productCount = remember(dataRevision) { ProductDatabaseManager.getProducts(context).size }
    val weeks = remember(reports) { recentWeekTotals(reports) }

    NovaScreen {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Reveal {
                HomeHero(
                    selectedDate = selectedDate,
                    hasDate = hasDate,
                    onPickDate = { datePickerDialog.show() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Reveal(delayMillis = 70) {
                Button(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Upload File Excel", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Nothing to summarise until an automation run has saved its first laporan.
            if (reports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Reveal(delayMillis = 140) {
                    Column {
                        SectionLabel("Ringkasan")
                        Spacer(modifier = Modifier.height(10.dp))
                        WeeklyPulseCard(weeks = weeks, onClick = onNavigateToLaporan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Reveal(delayMillis = 210) {
                Column {
                    SectionLabel("Lainnya")
                    Spacer(modifier = Modifier.height(10.dp))

                    MenuCard(
                        icon = Icons.Default.ShoppingCart,
                        title = "Kode Produk",
                        subtitle = "Daftar kode produk yang tersimpan",
                        badge = productCount.toString(),
                        onClick = onNavigateToProducts
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MenuCard(
                        icon = Icons.Default.List,
                        title = "Laporan",
                        subtitle = "Riwayat sellout yang sudah dikirim",
                        badge = reports.size.toString(),
                        onClick = onNavigateToLaporan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MenuCard(
                        icon = Icons.Default.Share,
                        title = "Cadangan Data",
                        subtitle = "Ekspor & impor kode produk dan laporan",
                        onClick = onNavigateToBackup
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            StatusDot(
                active = isServiceEnabled,
                label = if (isServiceEnabled) "Accessibility service aktif"
                else "Accessibility service belum aktif"
            )
        }
    }
}

/** Time-of-day greeting for the hero card. */
private fun greeting(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String = when (hour) {
    in 0..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

/**
 * The one loud element on the home screen: the greeting, and the day the next
 * upload will be filed under.
 */
@Composable
private fun HomeHero(
    selectedDate: String,
    hasDate: Boolean,
    onPickDate: () -> Unit
) {
    val accents = MaterialTheme.accents
    val relative = if (hasDate) relativeDayLabel(selectedDate) else null

    GradientCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = greeting(),
                style = MaterialTheme.typography.labelMedium,
                color = accents.onHeroMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Automate Nova",
                style = MaterialTheme.typography.headlineSmall,
                color = accents.onHero
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TARGET TANGGAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.onHeroMuted
                )
                if (relative != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill(
                        text = relative,
                        container = accents.onHero.copy(alpha = 0.18f),
                        contentColor = accents.onHero
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasDate) selectedDate else "Belum dipilih",
                style = MaterialTheme.typography.titleLarge,
                color = accents.onHero
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedButton(
                onClick = onPickDate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.onHero),
                border = BorderStroke(1.dp, accents.onHeroMuted)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasDate) "Ubah Tanggal" else "Pilih Tanggal")
            }
        }
    }
}

/**
 * This week's totals with the weeks behind it as a bar chart. The percentage
 * compares value against last week, and is left off when last week had nothing
 * to compare against.
 */
@Composable
private fun WeeklyPulseCard(weeks: List<WeekTotal>, onClick: () -> Unit) {
    if (weeks.isEmpty()) return
    val current = weeks.last()
    val previous = weeks.getOrNull(weeks.lastIndex - 1)
    val deltaPercent = if (previous != null && previous.totalPrice > 0) {
        Math.round((current.totalPrice - previous.totalPrice) * 100.0 / previous.totalPrice).toInt()
    } else {
        null
    }

    NovaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Minggu ini", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = current.rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (deltaPercent != null) {
                    val up = deltaPercent >= 0
                    Pill(
                        text = (if (up) "+" else "-") + Math.abs(deltaPercent) + "%",
                        container = if (up) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (up) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(label = "Laporan", modifier = Modifier.weight(1f)) {
                    AnimatedCount(
                        value = current.reportCount,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                StatTile(label = "Kuantitas", modifier = Modifier.weight(1f)) {
                    AnimatedCount(
                        value = current.totalQty,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                StatTile(label = "Nilai", modifier = Modifier.weight(1f)) {
                    AnimatedCount(
                        value = current.totalPrice,
                        style = MaterialTheme.typography.titleLarge,
                        format = { formatRupiahShort(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NovaBarChart(
                bars = weeks.mapIndexed { index, week ->
                    ChartBar(
                        label = week.label,
                        value = week.totalPrice.toLong(),
                        highlighted = index == weeks.lastIndex
                    )
                }
            )
        }
    }
}

@Composable
fun ProductListScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val products = remember { ProductDatabaseManager.getProducts(context) }

    NovaScreen {
        NovaHeader(
            title = "Kode Produk",
            subtitle = "${products.size} produk tersimpan",
            onBack = onNavigateBack
        )

        if (products.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ShoppingCart,
                title = "Belum ada kode produk",
                message = "Kode tersimpan otomatis setiap kali kamu mengisinya di halaman Data Uploaded."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products.toList()) { (name, code) ->
                    NovaCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Pill(
                                text = code,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
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
