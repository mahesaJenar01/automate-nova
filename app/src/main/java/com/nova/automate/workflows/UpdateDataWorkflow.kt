package com.nova.automate.workflows

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.automate.core.*

data class ProductItem(val code: String, val qty: String, val name: String, var price: Int = 0, var audited: Boolean = false)

class UpdateDataWorkflow(service: AccessibilityService) : AutomationWorkflow(service) {

    companion object {
        private const val TAG = "NovaAutomation"
    }

    private enum class State {
        IDLE,
        SEARCHING_HISTORI,
        VALIDATING_HISTORI_PAGE,
        SEARCHING_DATE,
        SEARCHING_UPDATE_DATA,
        VALIDATING_DETAIL_SUMMARY_PAGE,
        CLICKING_SELLOUT,
        CLICKING_UPDATE_SELLOUT,
        VALIDATING_SELLOUT_PAGE,
        CLICKING_OMG_FILTER,
        SEARCHING_OMG_RADIO,
        CLICKING_TERAPKAN,
        WAITING_FOR_PRODUCTS_PAGE,
        SEARCHING_PRODUCT,
        TYPING_IN_PROGRESS,
        WAITING_SEARCH_RESULT,
        INPUT_QUANTITY,
        CLICKING_LANJUT,
        VALIDATING_SUMMARY,
        AUDIT_SUMMARY_PAGE,
        CLICKING_TAMBAH_PRODUK,
        WAITING_FOR_SELLOUT_PAGE_AFTER_TAMBAH,
        GATHERING_SUMMARY_INFO,
        SCROLL_TO_SUBMIT,
        WAIT_BEFORE_SUBMIT_SELLOUT,
        CLICKING_SUBMIT_SELLOUT,
        WAITING_FOR_JUMLAH_STRUK,
        INPUT_RECEIPT_COUNT,
        CLICK_SUBMIT_STRUK
    }

    private var currentState = State.IDLE
    private var targetDateString: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var productsToAdd = mutableListOf<ProductItem>()
    private var currentProductIndex = 0

    override var isFinished: Boolean = false
        private set

    override fun start() {
        isFinished = false
        targetDateString = getTargetDate(service)
        productsToAdd.clear()
        
        val prefs = service.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("products_to_add", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val code = obj.getString("code")
                val qty = obj.getString("qty")
                val name = obj.optString("name", "Unknown Product")
                productsToAdd.add(ProductItem(code, qty, name))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse products_to_add", e)
        }

        // Consume the queue: this run owns the list now, so it can never be replayed later.
        prefs.edit().remove("products_to_add").apply()

        if (productsToAdd.isEmpty()) {
            Log.d(TAG, "No products to add. Stopping workflow.")
            isFinished = true
            return
        }

        currentState = State.SEARCHING_HISTORI
        Log.d(TAG, "Starting UpdateDataWorkflow! Target Date: $targetDateString. Waiting 5s...")
        
        handler.postDelayed({
            triggerSearch()
        }, 5000)
    }

    private fun getTargetDate(context: Context): String {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        // The day to write into, chosen on the Data Uploaded page. It is the date the data came
        // from unless "Input in Other Date" was used, so it is not the same as "target_date".
        return prefs.getString("run_target_date", "") ?: ""
    }

    override fun process(rootNode: AccessibilityNodeInfo?) {
    }

    fun triggerSearch() {
        if (currentState == State.IDLE) {
            currentState = State.IDLE
            Log.d(TAG, "Workflow finished or cancelled.")
            isFinished = true
            return
        }

        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.d(TAG, "Screen is currently unavailable (rootInActiveWindow is null).")
            scheduleNextSearch()
            return
        }

        var foundInCurrentStep = false

        when (currentState) {
            State.SEARCHING_HISTORI -> {
                Log.d(TAG, "Searching for 'Histori' element...")
                val targetNode = rootNode.findNodeByContentDescription("Histori")
                
                if (targetNode != null) {
                    if (targetNode.isClickable) {
                        if (targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.d(TAG, "SUCCESS! Clicked the 'Histori' icon.")
                            foundInCurrentStep = true
                            currentState = State.VALIDATING_HISTORI_PAGE
                        }
                    }
                }
            }

            State.VALIDATING_HISTORI_PAGE -> {
                Log.d(TAG, "Validating Histori Visit page...")
                val pageTitle = rootNode.findNodeByContentDescription("Histori Visit")
                if (pageTitle != null && !pageTitle.isClickable) {
                    Log.d(TAG, "SUCCESS! Reached Histori Visit page.")
                    foundInCurrentStep = true
                    currentState = State.SEARCHING_DATE
                }
            }
            
            State.SEARCHING_DATE -> {
                Log.d(TAG, "Searching for date: '$targetDateString'...")
                if (targetDateString.isBlank()) {
                    Log.d(TAG, "Target date is empty. Stopping.")
                    currentState = State.IDLE
                    isFinished = true
                    return
                }

                val dateNode = rootNode.findNodeByContentDescription(targetDateString)
                if (dateNode != null) {
                    Log.d(TAG, "SUCCESS! Found the date element.")
                    foundInCurrentStep = true
                    currentState = State.SEARCHING_UPDATE_DATA
                } else {
                    Log.d(TAG, "Date element not found on this screen. Determining scroll direction...")
                    val visibleDates = mutableListOf<java.util.Date>()
                    extractVisibleDates(rootNode, visibleDates)
                    
                    val scrollNode = rootNode.findScrollView()
                    if (scrollNode != null) {
                        var scrollDownList = true // default to scrolling down the list (swiping up)
                        
                        val targetDate = parseIndonesianDate(targetDateString)
                        if (targetDate != null && visibleDates.isNotEmpty()) {
                            val firstVisibleDate = visibleDates.first()
                            Log.d(TAG, "Target: $targetDateString | Top Visible: ${firstVisibleDate}")
                            
                            if (firstVisibleDate.before(targetDate)) {
                                scrollDownList = false
                            } else {
                                scrollDownList = true
                            }
                        } else {
                            Log.d(TAG, "Could not extract any visible dates. Defaulting to scroll down list.")
                        }

                        if (scrollDownList) {
                            Log.d(TAG, "Scrolling DOWN the list (swiping up)...")
                        } else {
                            Log.d(TAG, "Scrolling UP the list (swiping down)...")
                        }
                        
                        GestureHelper.performCustomScroll(service, scrollNode, 0.40f, 800L, scrollDownList)
                    } else {
                        Log.d(TAG, "No ScrollView found to scroll.")
                    }
                }
            }
            
            State.SEARCHING_UPDATE_DATA -> {
                Log.d(TAG, "Searching for 'Update Data' button for $targetDateString...")
                val dateNode = rootNode.findNodeByContentDescription(targetDateString)
                val scrollNode = rootNode.findScrollView()
                
                if (dateNode != null) {
                    val dateBounds = Rect()
                    dateNode.getBoundsInScreen(dateBounds)
                    
                    // 1. Identify the 'Next Date' to determine if the current section is fully visible
                    var nextDateTop = -1
                    val allVisibleDates = mutableListOf<Pair<java.util.Date, Int>>()
                    
                    fun findDates(node: AccessibilityNodeInfo) {
                        val desc = node.contentDescription?.toString()
                        if (desc != null) {
                            val parsed = parseIndonesianDate(desc)
                            if (parsed != null) {
                                val b = Rect()
                                node.getBoundsInScreen(b)
                                allVisibleDates.add(parsed to b.top)
                            }
                        }
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i)
                            if (child != null) {
                                findDates(child)
                                child.recycle()
                            }
                        }
                    }
                    findDates(rootNode)
                    
                    val targetParsed = parseIndonesianDate(targetDateString)
                    if (targetParsed != null) {
                        // Find the first date that appears below our target date on the screen
                        val sortedAfter = allVisibleDates
                            .filter { it.second > dateBounds.top + 10 }
                            .sortedBy { it.second }
                        
                        if (sortedAfter.isNotEmpty()) {
                            nextDateTop = sortedAfter.first().second
                        }
                    }

                    val screenBounds = Rect()
                    if (scrollNode != null) scrollNode.getBoundsInScreen(screenBounds)
                    else rootNode.getBoundsInScreen(screenBounds)
                    
                    // 2. Search for the button within the target section
                    // If nextDateTop exists, the section ends there. Otherwise, it goes to screen bottom.
                    val bottomLimit = if (nextDateTop != -1) nextDateTop else screenBounds.bottom
                    
                    var updateButton: AccessibilityNodeInfo? = null
                    fun findButtonInSection(node: AccessibilityNodeInfo) {
                        val b = Rect()
                        node.getBoundsInScreen(b)
                        // Button must be below date top and above the next date (or screen bottom)
                        if (b.top >= dateBounds.top - 10 && b.bottom <= bottomLimit + 10) {
                            val d = node.contentDescription?.toString() ?: ""
                            val t = node.text?.toString() ?: ""
                            if (d.contains("Update Data", true) || t.contains("Update Data", true)) {
                                updateButton = AccessibilityNodeInfo.obtain(node)
                                return
                            }
                        }
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i)
                            if (child != null) {
                                findButtonInSection(child)
                                child.recycle()
                                if (updateButton != null) return
                            }
                        }
                    }
                    findButtonInSection(rootNode)

                    if (updateButton != null) {
                        var nodeToClick = updateButton
                        while (nodeToClick != null && !nodeToClick.isClickable) {
                            nodeToClick = nodeToClick.parent
                        }
                        if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.d(TAG, "SUCCESS! Clicked the 'Update Data' button.")
                            foundInCurrentStep = true
                            currentState = State.VALIDATING_DETAIL_SUMMARY_PAGE
                        }
                    } else {
                        // 3. No button found. Either the section is fully visible (nothing to click
                        //    on that day) or it is still clipped and we need to scroll.
                        if (nextDateTop != -1) {
                            // The next date is visible, meaning the current date's section is COMPLETELY on screen.
                            val actualHeight = nextDateTop - dateBounds.top
                            Log.d(TAG, "Section for $targetDateString is complete. Height: $actualHeight. No 'Update Data' button. Stopping.")
                            abortAndRedirect(
                                "Tanggal $targetDateString tidak punya tombol 'Update Data' di Nova App.\n\n" +
                                    "Kalau tanggal itu libur, pilih tanggal lain lewat \"Input in Other Date\" lalu jalankan lagi."
                            )
                        } else {
                            // The next date isn't visible yet. The section might be clipped.
                            Log.d(TAG, "Section for $targetDateString might be clipped (Next date not found). Scrolling...")
                            if (scrollNode != null) {
                                GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 500L)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Lost the date node. Doing a small scroll to find it again...")
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 500L)
                    }
                }
            }

            State.VALIDATING_DETAIL_SUMMARY_PAGE -> {
                Log.d(TAG, "Validating Detail Summary page...")
                val pageTitle = rootNode.findNodeByContentDescription("Detail Summary")
                if (pageTitle != null && !pageTitle.isClickable) {
                    Log.d(TAG, "SUCCESS! Reached Detail Summary page.")
                    foundInCurrentStep = true
                    currentState = State.CLICKING_SELLOUT
                }
            }

            State.CLICKING_SELLOUT -> {
                Log.d(TAG, "Searching for 'Sellout' element...")
                val selloutNode = rootNode.findNodeByContentDescription("Sellout")
                if (selloutNode != null) {
                    if (selloutNode.isClickable) {
                        if (selloutNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.d(TAG, "SUCCESS! Clicked the 'Sellout' element.")
                            foundInCurrentStep = true
                            currentState = State.CLICKING_UPDATE_SELLOUT
                        }
                    } else {
                        Log.d(TAG, "Found 'Sellout' element but it is not clickable.")
                    }
                }
            }

            State.CLICKING_UPDATE_SELLOUT -> {
                Log.d(TAG, "Searching for 'Update Sellout' button...")
                val updateSelloutNode = rootNode.findNodeByContentDescription("Update Sellout")
                if (updateSelloutNode != null) {
                    var nodeToClick = updateSelloutNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked 'Update Sellout'.")
                        foundInCurrentStep = true
                        currentState = State.VALIDATING_SELLOUT_PAGE
                    } else {
                        Log.d(TAG, "Found 'Update Sellout' but couldn't click it.")
                    }
                } else {
                    Log.d(TAG, "Scrolling down the page to search 'Update Sellout' button...")
                    val scrollNode = rootNode.findScrollView()
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.40f, 800L, true)
                    } else {
                        Log.d(TAG, "No ScrollView found to scroll.")
                    }
                }
            }

            State.VALIDATING_SELLOUT_PAGE -> {
                Log.d(TAG, "Validating Sellout page...")
                val pageTitle = rootNode.findNodeByContentDescription("Sellout")
                if (pageTitle != null && !pageTitle.isClickable) { 
                    Log.d(TAG, "SUCCESS! Reached Sellout page.")
                    foundInCurrentStep = true
                    currentState = State.CLICKING_OMG_FILTER
                } else if (pageTitle != null) {
                    Log.d(TAG, "Found 'Sellout' but it is clickable (might still be on previous page). Waiting...")
                }
            }

            State.CLICKING_OMG_FILTER -> {
                Log.d(TAG, "Searching for 'OMG' filter view...")
                val omgNode = rootNode.findNodeByContentDescription("OMG")
                if (omgNode != null) {
                    var nodeToClick = omgNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked the 'OMG' filter view.")
                        foundInCurrentStep = true
                        currentState = State.SEARCHING_OMG_RADIO
                    } else {
                        Log.d(TAG, "Found 'OMG' but couldn't click it.")
                    }
                }
            }

            State.SEARCHING_OMG_RADIO -> {
                Log.d(TAG, "Searching for 'OMG' RadioButton...")
                var omgRadioNode: AccessibilityNodeInfo? = null
                fun findRadio(node: AccessibilityNodeInfo) {
                    if (node.contentDescription?.toString() == "OMG" && node.className?.toString() == "android.widget.RadioButton") {
                        omgRadioNode = AccessibilityNodeInfo.obtain(node)
                        return
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            findRadio(child)
                            if (omgRadioNode != null) return
                        }
                    }
                }
                findRadio(rootNode)

                if (omgRadioNode != null) {
                    var nodeToClick = omgRadioNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked the 'OMG' RadioButton.")
                        foundInCurrentStep = true
                        currentState = State.CLICKING_TERAPKAN
                    } else {
                        Log.d(TAG, "Found 'OMG' RadioButton but couldn't click it.")
                    }
                    omgRadioNode?.recycle()
                } else {
                    Log.d(TAG, "'OMG' RadioButton not found. Scrolling down...")
                    val scrollNode = rootNode.findScrollView()
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.40f, 800L, true)
                    } else {
                        Log.d(TAG, "No ScrollView found to scroll.")
                    }
                }
            }

            State.CLICKING_TERAPKAN -> {
                Log.d(TAG, "Searching for 'Terapkan' button...")
                val terapkanNode = rootNode.findNodeByContentDescription("Terapkan")
                if (terapkanNode != null) {
                    var nodeToClick = terapkanNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked 'Terapkan'. Waiting 5s for products page...")
                        foundInCurrentStep = true
                        currentState = State.WAITING_FOR_PRODUCTS_PAGE
                        handler.postDelayed({
                            if (currentState == State.WAITING_FOR_PRODUCTS_PAGE) {
                                currentState = State.SEARCHING_PRODUCT
                                triggerSearch()
                            }
                        }, 5000)
                        return // Exit to let the delay handle the next step
                    } else {
                        Log.d(TAG, "Found 'Terapkan' but couldn't click it.")
                    }
                } else {
                    Log.d(TAG, "'Terapkan' button not found.")
                }
            }
            
            State.WAITING_FOR_PRODUCTS_PAGE -> {
                Log.d(TAG, "Waiting for products page (5s)... Ignoring UI changes.")
                // No-op
            }
            
            State.SEARCHING_PRODUCT -> {
                var foundUnaudited = false
                while(currentProductIndex < productsToAdd.size) {
                    if (!productsToAdd[currentProductIndex].audited) {
                        foundUnaudited = true
                        break
                    }
                    currentProductIndex++
                }

                val currentProduct = productsToAdd.getOrNull(currentProductIndex)
                if (currentProduct == null || !foundUnaudited) {
                    Log.d(TAG, "All products processed! Moving to CLICKING_LANJUT.")
                    currentState = State.CLICKING_LANJUT
                    triggerSearch()
                    return
                }
                
                val code = currentProduct.code
                Log.d(TAG, "Searching product code: $code")
                
                val searchBox = rootNode.findNodeByClassName("android.widget.EditText") ?: rootNode.findNodeByHint("Cari nama produk")
                
                if (searchBox != null) {
                    searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val currentBox = service.rootInActiveWindow?.findNodeByClassName("android.widget.EditText") ?: searchBox
                        currentBox.typeTextOneByOne(code, handler) {
                            Log.d(TAG, "SUCCESS! Typed product code $code.")
                            currentState = State.WAITING_SEARCH_RESULT
                            triggerSearch()
                        }
                    }, 500)
                    currentState = State.TYPING_IN_PROGRESS
                } else {
                    Log.d(TAG, "Search box with hint 'Cari nama produk' not found.")
                }
            }

            State.TYPING_IN_PROGRESS -> {
                Log.d(TAG, "Typing in progress... waiting.")
                // No-op
            }

            State.WAITING_SEARCH_RESULT -> {
                val code = productsToAdd[currentProductIndex].code
                val descToWait = "Hasil pencarian untuk \"$code\""
                Log.d(TAG, "Waiting for search result: $descToWait")
                
                val resultNode = rootNode.findNodeByContentDescription(descToWait)
                if (resultNode != null) {
                    Log.d(TAG, "SUCCESS! Found search result for $code.")
                    foundInCurrentStep = true
                    currentState = State.INPUT_QUANTITY
                }
            }
            
            State.INPUT_QUANTITY -> {
                val code = productsToAdd[currentProductIndex].code
                val qty = productsToAdd[currentProductIndex].qty
                Log.d(TAG, "Inputting quantity $qty for $code...")
                
                var qtyNode: AccessibilityNodeInfo? = null
                fun findQtyBox(node: AccessibilityNodeInfo) {
                    if (node.className == "android.widget.EditText") {
                        val textStr = node.text?.toString() ?: ""
                        val hintStr = node.hintText?.toString() ?: ""
                        if (textStr != code && hintStr != "Cari nama produk") {
                            qtyNode = AccessibilityNodeInfo.obtain(node)
                        }
                    }
                    if (qtyNode != null) return
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            findQtyBox(child)
                            child.recycle()
                            if (qtyNode != null) return
                        }
                    }
                }
                findQtyBox(rootNode)
                
                if (qtyNode != null) {
                    var extractedPrice = 0
                    var parent = qtyNode.parent
                    var depth = 0
                    while (parent != null && depth < 5) {
                        var priceStr = ""
                        fun searchPrice(n: AccessibilityNodeInfo) {
                            val desc = n.contentDescription?.toString()
                            val text = n.text?.toString()
                            if (desc != null && desc.startsWith("Rp ")) priceStr = desc
                            else if (text != null && text.startsWith("Rp ")) priceStr = text
                            if (priceStr.isNotEmpty()) return
                            for (i in 0 until n.childCount) {
                                val child = n.getChild(i)
                                if (child != null) {
                                    searchPrice(child)
                                    child.recycle()
                                    if (priceStr.isNotEmpty()) return
                                }
                            }
                        }
                        searchPrice(parent)
                        if (priceStr.isNotEmpty()) {
                            val cleanStr = priceStr.replace(Regex("[^0-9]"), "")
                            if (cleanStr.isNotEmpty()) {
                                extractedPrice = cleanStr.toInt()
                                break
                            }
                        }
                        val oldParent = parent
                        parent = parent.parent
                        oldParent.recycle()
                        depth++
                    }
                    if (extractedPrice > 0) {
                        Log.d(TAG, "SUCCESS! Extracted price: $extractedPrice")
                        productsToAdd[currentProductIndex].price = extractedPrice
                    } else {
                        Log.d(TAG, "WARNING! Could not extract price.")
                    }

                    qtyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        var currentQtyNode: AccessibilityNodeInfo? = null
                        fun findCurrentQty(n: AccessibilityNodeInfo) {
                            if (n.className == "android.widget.EditText" && n.text?.toString() != code && n.hintText?.toString() != "Cari nama produk") {
                                currentQtyNode = AccessibilityNodeInfo.obtain(n)
                            }
                            if (currentQtyNode != null) return
                            for (i in 0 until n.childCount) {
                                val c = n.getChild(i)
                                if (c != null) {
                                    findCurrentQty(c)
                                    c.recycle()
                                    if (currentQtyNode != null) return
                                }
                            }
                        }
                        if (service.rootInActiveWindow != null) {
                            findCurrentQty(service.rootInActiveWindow!!)
                        }
                        
                        val finalNode = currentQtyNode ?: qtyNode
                        finalNode.typeTextOneByOne(qty, handler) {
                            Log.d(TAG, "SUCCESS! Typed quantity $qty for $code.")
                            currentProductIndex++
                            currentState = State.SEARCHING_PRODUCT
                            triggerSearch()
                        }
                    }, 500)
                    currentState = State.TYPING_IN_PROGRESS
                } else {
                    Log.d(TAG, "EditText for quantity not found. It might be that the page hasn't fully rendered.")
                }
            }
            
            State.CLICKING_LANJUT -> {
                Log.d(TAG, "Clicking Lanjut button...")
                val lanjutNode = rootNode.findNodeByContentDescription("Lanjut")
                if (lanjutNode != null) {
                    if (lanjutNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Lanjut.")
                        foundInCurrentStep = true
                        currentState = State.VALIDATING_SUMMARY
                    }
                }
            }

            State.VALIDATING_SUMMARY -> {
                Log.d(TAG, "Validating Summary page...")
                val summaryNode = rootNode.findNodeByContentDescription("Summary")
                if (summaryNode != null) {
                    Log.d(TAG, "SUCCESS! Reached Summary page.")
                    foundInCurrentStep = true
                    currentState = State.AUDIT_SUMMARY_PAGE
                }
            }

            State.AUDIT_SUMMARY_PAGE -> {
                Log.d(TAG, "Auditing Summary page...")
                
                var foundAnyEditText = false
                fun scanNodeForProducts(node: AccessibilityNodeInfo) {
                    if (node.className == "android.widget.EditText") {
                        val qtyStr = node.text?.toString() ?: ""
                        val hintStr = node.hintText?.toString() ?: ""
                        if (hintStr.isNotEmpty()) {
                            val code = hintStr.substringBefore("\n").trim()
                            val product = productsToAdd.find { it.code == code && !it.audited }
                            if (product != null) {
                                if (product.qty == qtyStr) {
                                    product.audited = true
                                    Log.d(TAG, "SUCCESS! Audited product: $code")
                                } else {
                                    Log.d(TAG, "WARNING! Quantity mismatch for $code. Expected ${product.qty}, found $qtyStr. Will fix via Tambah Produk.")
                                }
                            }
                        }
                        foundAnyEditText = true
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            scanNodeForProducts(child)
                            child.recycle()
                        }
                    }
                }
                scanNodeForProducts(rootNode)
                
                val submitNode = rootNode.findNodeByContentDescription("Submit Sellout") ?: rootNode.findNodeByText("Submit Sellout")
                if (submitNode != null) {
                    Log.d(TAG, "Submit Sellout is visible. Finished scanning.")
                    val unaudited = productsToAdd.filter { !it.audited }
                    if (unaudited.isEmpty()) {
                        Log.d(TAG, "All products audited successfully! Proceeding to GATHERING_SUMMARY_INFO.")
                        currentState = State.GATHERING_SUMMARY_INFO
                        triggerSearch()
                    } else {
                        Log.d(TAG, "Audit failed! Missing products: ${unaudited.map { it.code }}. Clicking Tambah Produk.")
                        currentState = State.CLICKING_TAMBAH_PRODUK
                        triggerSearch()
                    }
                } else {
                    Log.d(TAG, "Submit Sellout not visible yet. Scrolling down smoothly and slowly...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, true)
                    }
                }
            }

            State.CLICKING_TAMBAH_PRODUK -> {
                Log.d(TAG, "Looking for Tambah Produk button...")
                val tambahNode = rootNode.findNodeByContentDescOrText("Tambah Produk")
                if (tambahNode != null) {
                    var nodeToClick = tambahNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Tambah Produk.")
                        currentProductIndex = 0 // Reset for next iteration
                        currentState = State.WAITING_FOR_SELLOUT_PAGE_AFTER_TAMBAH
                    }
                } else {
                    Log.d(TAG, "Tambah Produk not found, scrolling up smoothly to find it...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, false)
                    }
                }
            }

            State.WAITING_FOR_SELLOUT_PAGE_AFTER_TAMBAH -> {
                Log.d(TAG, "Waiting for Sellout page (Cari nama produk) to appear...")
                val searchBox = rootNode.findNodeByClassName("android.widget.EditText") ?: rootNode.findNodeByHint("Cari nama produk")
                if (searchBox != null) {
                    Log.d(TAG, "SUCCESS! Reached Sellout page again.")
                    currentState = State.SEARCHING_PRODUCT
                    triggerSearch()
                }
            }

            State.GATHERING_SUMMARY_INFO -> {
                Log.d(TAG, "Gathering Summary Info...")
                var itemsText = ""
                var priceText = ""
                
                fun findInfo(node: AccessibilityNodeInfo) {
                    val desc = node.contentDescription?.toString()
                    if (desc != null) {
                        if (desc.startsWith("Total Harga")) itemsText = desc
                        if (desc.startsWith("Rp ")) priceText = desc
                    }
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            findInfo(child)
                            child.recycle()
                        }
                    }
                }
                
                findInfo(rootNode)
                
                if (itemsText.isNotEmpty() && priceText.isNotEmpty()) {
                    Log.d(TAG, "SUCCESS! Gathered Info: $itemsText | $priceText")
                    foundInCurrentStep = true
                    currentState = State.SCROLL_TO_SUBMIT
                } else {
                    Log.d(TAG, "Could not find Summary Info. Scrolling down smoothly...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, true)
                    }
                }
            }
            
            State.SCROLL_TO_SUBMIT -> {
                Log.d(TAG, "Searching for Submit Sellout button...")
                val submitNode = rootNode.findNodeByContentDescription("Submit Sellout") ?: rootNode.findNodeByText("Submit Sellout")
                var found = false
                
                if (submitNode != null) {
                    var nodeToClick = submitNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null) {
                        Log.d(TAG, "SUCCESS! Found Submit Sellout. Waiting 2 seconds before clicking...")
                        foundInCurrentStep = true
                        currentState = State.WAIT_BEFORE_SUBMIT_SELLOUT
                        handler.postDelayed({
                            if (currentState == State.WAIT_BEFORE_SUBMIT_SELLOUT) {
                                currentState = State.CLICKING_SUBMIT_SELLOUT
                                triggerSearch()
                            }
                        }, 2000)
                        found = true
                        return // Exit to let the delay handle the next step
                    }
                }
                
                if (!found) {
                    Log.d(TAG, "Submit Sellout not found or not clickable. Scrolling down smoothly...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, true)
                    }
                }
            }

            State.WAIT_BEFORE_SUBMIT_SELLOUT -> {
                Log.d(TAG, "Waiting 2s before clicking Submit Sellout...")
                // No-op
            }

            State.CLICKING_SUBMIT_SELLOUT -> {
                Log.d(TAG, "Clicking Submit Sellout button...")
                val submitNode = rootNode.findNodeByContentDescription("Submit Sellout") ?: rootNode.findNodeByText("Submit Sellout")
                if (submitNode != null) {
                    var nodeToClick = submitNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Submit Sellout.")
                        foundInCurrentStep = true
                        currentState = State.WAITING_FOR_JUMLAH_STRUK
                    } else {
                        Log.d(TAG, "Found Submit Sellout but couldn't click it.")
                    }
                } else {
                    Log.d(TAG, "Submit Sellout not found when trying to click it.")
                }
            }

            State.WAITING_FOR_JUMLAH_STRUK -> {
                Log.d(TAG, "Waiting for 'Jumlah Struk' to appear...")
                val jumlahStrukNode = rootNode.findNodeByContentDescription("Jumlah Struk") ?: rootNode.findNodeByText("Jumlah Struk")
                if (jumlahStrukNode != null) {
                    Log.d(TAG, "SUCCESS! 'Jumlah Struk' appeared.")
                    foundInCurrentStep = true
                    currentState = State.INPUT_RECEIPT_COUNT
                }
            }

            State.INPUT_RECEIPT_COUNT -> {
                Log.d(TAG, "Inputting Receipt Count...")
                val receiptNode = rootNode.findNodeByHint("Masukkan jumlah struk")
                if (receiptNode != null) {
                    receiptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val currentReceiptNode = service.rootInActiveWindow?.findNodeByHint("Masukkan jumlah struk") ?: receiptNode
                        currentReceiptNode.typeTextOneByOne("1", handler) {
                            Log.d(TAG, "SUCCESS! Typed receipt count: 1.")
                            currentState = State.CLICK_SUBMIT_STRUK
                            triggerSearch()
                        }
                    }, 500)
                    currentState = State.TYPING_IN_PROGRESS
                }
            }
            
            State.CLICK_SUBMIT_STRUK -> {
                Log.d(TAG, "Clicking 'Submit Struk'. WORKFLOW COMPLETE.")
                val submitStruk = rootNode.findNodeByContentDescription("Submit Struk")
                if (submitStruk != null) {
                    submitStruk.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                
                // --- COMPILE AND SAVE LAPORAN DATA ---
                val prefs = service.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val existingData = prefs.getString("laporan_data", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(existingData)
                
                var totalQty = 0
                var totalPrice = 0
                val itemsArray = org.json.JSONArray()
                for (item in productsToAdd) {
                    val q = item.qty.toIntOrNull() ?: 0
                    val p = item.price
                    totalQty += q
                    totalPrice += (q * p)
                    
                    val itemObj = org.json.JSONObject()
                    itemObj.put("code", item.code)
                    itemObj.put("name", item.name)
                    itemObj.put("qty", item.qty)
                    itemObj.put("price", p)
                    itemObj.put("totalPrice", q * p)
                    itemsArray.put(itemObj)
                }
                
                val reportObj = org.json.JSONObject()
                reportObj.put("date", targetDateString)
                reportObj.put("totalQty", totalQty)
                reportObj.put("totalPrice", totalPrice)
                reportObj.put("items", itemsArray)
                
                jsonArray.put(reportObj)
                prefs.edit().putString("laporan_data", jsonArray.toString()).apply()
                // --- END COMPILE ---
                
                // --- LAUNCH AUTOMATE NOVA ---
                val intent = service.packageManager.getLaunchIntentForPackage("com.nova.automate")
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    service.startActivity(intent)
                }

                currentState = State.IDLE
                isFinished = true
            }
            
            else -> {}
        }

        if (!foundInCurrentStep && currentState != State.IDLE) {
            val omittedStates = listOf(
                State.SEARCHING_DATE,
                State.CLICKING_UPDATE_SELLOUT,
                State.SEARCHING_OMG_RADIO,
                State.TYPING_IN_PROGRESS,
                State.WAITING_FOR_PRODUCTS_PAGE,
                State.WAITING_SEARCH_RESULT,
                State.WAIT_BEFORE_SUBMIT_SELLOUT,
                State.WAIT_BEFORE_SUBMIT_SELLOUT,
                State.WAITING_FOR_JUMLAH_STRUK,
                State.AUDIT_SUMMARY_PAGE,
                State.GATHERING_SUMMARY_INFO,
                State.SCROLL_TO_SUBMIT
            )
            if (omittedStates.contains(currentState)) {
                Log.d(TAG, "Still searching/scrolling/waiting, omitted screen dump to avoid log spam.")
            } else {
                Log.d(TAG, "Step failed. --- DUMPING ALL SCREEN ELEMENTS FOR DEBUGGING ---")
                rootNode.logAllScreenElements()
                Log.d(TAG, "--- END OF SCREEN DUMP ---")
            }
        }

        if (currentState != State.IDLE) {
            Log.d(TAG, "Waiting before next action...")
            scheduleNextSearch()
        }
    }

    /**
     * Stops the run and returns to Automate Nova, leaving a note explaining why so the app can
     * show it once it is back in the foreground.
     */
    private fun abortAndRedirect(message: String) {
        val prefs = service.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_run_message", message).apply()

        val intent = service.packageManager.getLaunchIntentForPackage("com.nova.automate")
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            service.startActivity(intent)
        }
        
        currentState = State.IDLE
        isFinished = true
    }

    private fun extractVisibleDates(node: AccessibilityNodeInfo, dateList: MutableList<java.util.Date>) {
        val desc = node.contentDescription?.toString()
        if (desc != null) {
            val date = parseIndonesianDate(desc)
            if (date != null) {
                dateList.add(date)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractVisibleDates(child, dateList)
            }
        }
    }

    private fun parseIndonesianDate(dateString: String): java.util.Date? {
        try {
            val format = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale("id", "ID"))
            return format.parse(dateString)
        } catch (e: Exception) {
            return null
        }
    }

    private fun scheduleNextSearch() {
        val delay = when (currentState) {
            State.AUDIT_SUMMARY_PAGE,
            State.CLICKING_TAMBAH_PRODUK,
            State.GATHERING_SUMMARY_INFO,
            State.SCROLL_TO_SUBMIT -> 1500L
            else -> 2000L
        }
        searchRunnable?.let { handler.removeCallbacks(it) }
        searchRunnable = Runnable {
            if (!isFinished) triggerSearch()
        }
        handler.postDelayed(searchRunnable!!, delay)
    }

    override fun cancel() {
        currentState = State.IDLE
        isFinished = true
        searchRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }
}
