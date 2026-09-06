package com.nova.automate

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads and writes a single backup file holding everything the app keeps locally:
 * the product code database, every saved laporan, and the selected target date.
 *
 * The file is plain JSON so it stays readable and can be repaired by hand if a
 * transfer between phones goes wrong.
 */
object BackupManager {

    const val FORMAT_VERSION = 1
    private const val APP_ID = "com.nova.automate"

    /** What an import found in the file, shown to the user before anything is written. */
    class BackupContent(
        val productCount: Int,
        val laporanCount: Int,
        val targetDate: String?,
        val exportedAt: String?,
        internal val products: Map<String, String>,
        internal val laporan: JSONArray
    )

    /** How an import folds into what is already on the phone. */
    enum class ImportMode {
        /** Keep everything already saved, add only what is missing. */
        MERGE,

        /** Throw away what is saved and keep only the file's contents. */
        REPLACE
    }

    data class ImportResult(
        val productsAdded: Int,
        val laporanAdded: Int,
        val targetDateApplied: Boolean
    )

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        return "automate-nova-backup-$stamp.json"
    }

    // --- Export -------------------------------------------------------------

    /** Serialises the whole app state into the JSON that lands in the picked file. */
    fun buildBackupJson(context: Context): String {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val productsObj = JSONObject()
        // The merged view, so a restore onto a fresh install carries the bundled
        // codes too and never depends on the asset file staying in sync.
        for ((name, code) in ProductDatabaseManager.getProducts(context)) {
            productsObj.put(name, code)
        }

        val laporanArray = try {
            JSONArray(prefs.getString("laporan_data", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("targetDate", prefs.getString("target_date", null) ?: JSONObject.NULL)
        root.put("products", productsObj)
        root.put("laporan", laporanArray)
        return root.toString(2)
    }

    /** Writes the backup into the file the user picked. Returns the byte size written. */
    fun exportTo(context: Context, uri: Uri): Int {
        val bytes = buildBackupJson(context).toByteArray(Charsets.UTF_8)
        // "wt" truncates first, so re-picking an existing file can't leave a tail
        // of the older, longer backup behind. Not every storage provider offers
        // that mode, so fall back to a plain write when it is refused.
        val stream = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            context.contentResolver.openOutputStream(uri)
        } ?: throw IllegalStateException("Tidak bisa membuka file untuk ditulis.")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
        return bytes.size
    }

    // --- Import -------------------------------------------------------------

    /** Parses and validates the picked file without touching any stored data yet. */
    fun readBackup(context: Context, uri: Uri): BackupContent {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Tidak bisa membaca file.")

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("File ini bukan file cadangan Automate Nova.")
        }

        if (root.optString("app") != APP_ID) {
            throw IllegalArgumentException("File ini bukan file cadangan Automate Nova.")
        }
        if (root.optInt("version", 0) > FORMAT_VERSION) {
            throw IllegalArgumentException(
                "File ini dibuat oleh versi aplikasi yang lebih baru. Perbarui aplikasinya dulu."
            )
        }

        val productsObj = root.optJSONObject("products") ?: JSONObject()
        val products = mutableMapOf<String, String>()
        val keys = productsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val code = productsObj.optString(key)
            if (code.isNotBlank()) products[key] = code
        }

        val laporan = root.optJSONArray("laporan") ?: JSONArray()
        val targetDate = if (root.isNull("targetDate")) null else root.optString("targetDate")
        val exportedAt = root.optString("exportedAt")

        return BackupContent(
            productCount = products.size,
            laporanCount = laporan.length(),
            targetDate = targetDate?.takeIf { it.isNotBlank() },
            exportedAt = exportedAt.takeIf { it.isNotBlank() },
            products = products,
            laporan = laporan
        )
    }

    fun applyBackup(context: Context, content: BackupContent, mode: ImportMode): ImportResult {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // --- Products ---
        val productsAdded: Int
        if (mode == ImportMode.REPLACE) {
            ProductDatabaseManager.replaceCustomProducts(context, content.products)
            productsAdded = content.products.size
        } else {
            // MERGE never overwrites a code already on the phone, so a stale
            // backup can't silently undo a correction made here.
            val existing = ProductDatabaseManager.getProducts(context)
            val toAdd = content.products.filterKeys { !existing.containsKey(it) }
            if (toAdd.isNotEmpty()) ProductDatabaseManager.saveProducts(context, toAdd)
            productsAdded = toAdd.size
        }

        // --- Laporan ---
        val incoming = content.laporan
        val merged = JSONArray()
        var laporanAdded = 0
        if (mode == ImportMode.REPLACE) {
            for (i in 0 until incoming.length()) {
                incoming.optJSONObject(i)?.let { merged.put(it) }
            }
            laporanAdded = merged.length()
        } else {
            val existing = try {
                JSONArray(prefs.getString("laporan_data", "[]") ?: "[]")
            } catch (e: Exception) {
                JSONArray()
            }
            val seen = mutableSetOf<String>()
            for (i in 0 until existing.length()) {
                val obj = existing.optJSONObject(i) ?: continue
                seen.add(reportSignature(obj))
                merged.put(obj)
            }
            for (i in 0 until incoming.length()) {
                val obj = incoming.optJSONObject(i) ?: continue
                if (seen.add(reportSignature(obj))) {
                    merged.put(obj)
                    laporanAdded++
                }
            }
        }
        prefs.edit().putString("laporan_data", sortByDateAscending(merged).toString()).apply()

        // --- Target date ---
        val applyDate = content.targetDate != null &&
            (mode == ImportMode.REPLACE || prefs.getString("target_date", null) == null)
        if (applyDate) {
            prefs.edit().putString("target_date", content.targetDate).apply()
        }

        return ImportResult(
            productsAdded = productsAdded,
            laporanAdded = laporanAdded,
            targetDateApplied = applyDate
        )
    }

    /**
     * Identity of a report for de-duplication: two runs of the same data on the
     * same day produce identical fields, so this is enough to avoid doubling up
     * when a backup is imported twice.
     */
    private fun reportSignature(obj: JSONObject): String {
        val builder = StringBuilder()
        builder.append(obj.optString("date")).append("|")
        builder.append(obj.optString("inputDate")).append("|")
        builder.append(obj.optInt("totalQty")).append("|")
        builder.append(obj.optInt("totalPrice")).append("|")
        val items = obj.optJSONArray("items") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            builder.append(item.optString("code")).append(":")
                .append(item.optString("qty")).append(":")
                .append(item.optInt("price")).append(";")
        }
        return builder.toString()
    }

    /** Storage keeps reports oldest first; the list screen reverses them again. */
    private fun sortByDateAscending(array: JSONArray): JSONArray {
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { list.add(it) }
        }
        // Reports whose date can't be parsed keep their relative order at the end.
        val sorted = list.sortedBy { parseIndonesianDate(it.optString("date"))?.time ?: Long.MAX_VALUE }
        val result = JSONArray()
        for (obj in sorted) result.put(obj)
        return result
    }
}
