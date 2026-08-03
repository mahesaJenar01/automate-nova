package com.nova.automate

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.math.BigDecimal

data class ProcessedItem(
    val originalName: String,
    val normalizedName: String,
    val qty: Int,
    val productCode: String?
)

object ExcelProcessor {

    private val OMG_PREFIX = Regex("^omg(\\b|_)", RegexOption.IGNORE_CASE)

    fun normalizeProductName(name: String?): String {
        if (name == null) return ""
        var n = name.lowercase()
        val brands = listOf("oh my glam", "oh my glow", "omg")
        for (brand in brands) {
            n = n.replace(brand, "")
        }
        n = n.replace(".", ",")
        n = n.replace("\\s+".toRegex(), "")
        return n
    }

    /**
     * Entry point. Two layouts are supported:
     * 1. The "brand" layout: a header row with a Brand column, the product name and qty
     *    columns are detected by content.
     * 2. The "OMG prefix" layout: no brand column at all, product name in column A
     *    (always prefixed with "OMG"), sales qty in column G.
     *
     * Layout 1 is tried first; if the sheet has no brand column (or nothing usable came
     * out of it) we fall back to layout 2.
     */
    fun processExcel(context: Context, uri: Uri, productDb: Map<String, String>): List<ProcessedItem> {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val workbook = WorkbookFactory.create(inputStream)
        try {
            val sheet = workbook.getSheetAt(0)
            val byBrand = processByBrandColumn(sheet, productDb)
            if (byBrand.isNotEmpty()) return byBrand
            return processByOmgPrefix(sheet, productDb)
        } finally {
            workbook.close()
        }
    }

    private fun processByBrandColumn(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        productDb: Map<String, String>
    ): List<ProcessedItem> {
        val normDbKeys = productDb.keys.associateBy { normalizeProductName(it) }

        var headerRowIdx = -1
        var brandColIdx = -1

        for (i in 0..19) {
            val row = sheet.getRow(i) ?: continue
            for (j in 0 until row.lastCellNum) {
                val cell = row.getCell(j)
                val valStr = getCellValueAsString(cell).lowercase()
                if (valStr.contains("brand")) {
                    headerRowIdx = i
                    brandColIdx = j
                    break
                }
            }
            if (brandColIdx != -1) break
        }

        if (brandColIdx == -1) {
            return emptyList()
        }

        val headerRow = sheet.getRow(headerRowIdx)
        val columns = mutableMapOf<Int, String>()
        for (j in 0 until headerRow.lastCellNum) {
            columns[j] = getCellValueAsString(headerRow.getCell(j))
        }
        
        val omgRows = mutableListOf<org.apache.poi.ss.usermodel.Row>()
        for (i in headerRowIdx + 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val brandVal = getCellValueAsString(row.getCell(brandColIdx))
            if (brandVal.contains("OMG", ignoreCase = true)) {
                omgRows.add(row)
            }
        }
        
        var nameColIdx = -1
        var qtyColIdx = -1
        
        for (j in 0 until headerRow.lastCellNum) {
            if (j == brandColIdx) continue
            val colName = columns[j] ?: continue
            
            var matchesDb = 0
            var isNumeric = true
            var maxVal = 0.0
            var hasData = false
            
            for (row in omgRows) {
                val cellVal = getCellValueAsString(row.getCell(j))
                if (cellVal.isNotBlank()) {
                    hasData = true
                    val normVal = normalizeProductName(cellVal)
                    if (normDbKeys.containsKey(normVal)) {
                        matchesDb++
                    }
                    val num = cellVal.toDoubleOrNull()
                    if (num != null) {
                        if (num > maxVal) maxVal = num
                    } else {
                        isNumeric = false
                    }
                }
            }
            
            if (matchesDb > 0 && nameColIdx == -1) {
                nameColIdx = j
            } else if (hasData && isNumeric && maxVal < 50000 && !colName.lowercase().contains("barcode") && qtyColIdx == -1) {
                qtyColIdx = j
            }
        }
        
        if (nameColIdx == -1 || qtyColIdx == -1) {
            return emptyList()
        }

        val grouped = mutableMapOf<String, ProcessedItem>()

        for (row in omgRows) {
            val nameVal = getCellValueAsString(row.getCell(nameColIdx)).trim()
            val qtyStr = getCellValueAsString(row.getCell(qtyColIdx))
            val qty = qtyStr.toDoubleOrNull()?.toInt() ?: 0
            addToGroup(grouped, nameVal, qty, productDb, normDbKeys)
        }

        return sortItems(grouped)
    }

    /**
     * Fallback layout: no brand column. Every row whose column A starts with "OMG" is a
     * product row, and the sales qty sits in column G.
     */
    private fun processByOmgPrefix(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        productDb: Map<String, String>
    ): List<ProcessedItem> {
        val normDbKeys = productDb.keys.associateBy { normalizeProductName(it) }

        val nameColIdx = 0 // column A
        val qtyColIdx = 6  // column G

        val grouped = mutableMapOf<String, ProcessedItem>()

        for (i in 0..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val nameVal = getCellValueAsString(row.getCell(nameColIdx)).trim()
            if (!isOmgProductName(nameVal)) continue

            val qtyStr = getCellValueAsString(row.getCell(qtyColIdx))
            val qty = qtyStr.toDoubleOrNull()?.toInt() ?: 0
            addToGroup(grouped, nameVal, qty, productDb, normDbKeys)
        }

        return sortItems(grouped)
    }

    /** "OMG GLAM EVERY DAY PACKAGE" -> true, "OMGWHATEVER" / "SOMETHING OMG" -> false. */
    private fun isOmgProductName(name: String): Boolean {
        return OMG_PREFIX.containsMatchIn(name)
    }

    private fun addToGroup(
        grouped: MutableMap<String, ProcessedItem>,
        nameVal: String,
        qty: Int,
        productDb: Map<String, String>,
        normDbKeys: Map<String, String>
    ) {
        if (qty <= 0) return
        val normName = normalizeProductName(nameVal)
        if (normName.isBlank()) return

        val existing = grouped[normName]
        if (existing != null) {
            grouped[normName] = existing.copy(qty = existing.qty + qty)
        } else {
            val originalKey = normDbKeys[normName]
            val code = if (originalKey != null) productDb[originalKey] else null
            grouped[normName] = ProcessedItem(nameVal, normName, qty, code)
        }
    }

    private fun sortItems(grouped: Map<String, ProcessedItem>): List<ProcessedItem> {
        return grouped.values.toList()
            .sortedWith(compareBy<ProcessedItem> { it.productCode != null }.thenByDescending { it.qty })
    }

    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    // Avoid scientific notation (e.g. 1.0E3) by returning plain string 
                    // However, we only care about integers mostly. 
                    val bd = BigDecimal(cell.numericCellValue)
                    if (bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0) {
                        bd.toBigInteger().toString()
                    } else {
                        bd.toPlainString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue.toString()
                } catch (e: Exception) {
                    try {
                        cell.stringCellValue
                    } catch (e2: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }
}
