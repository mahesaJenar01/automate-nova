package com.nova.automate.workflows

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.automate.core.*

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
        GATHERING_SUMMARY_INFO,
        SCROLL_TO_SUBMIT,
        INPUT_RECEIPT_COUNT,
        CLICK_SUBMIT_STRUK
    }

    private var currentState = State.IDLE
    private var targetDateString: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val productsToAdd = listOf(Pair("80277", "5"), Pair("80098", "3"))
    private var currentProductIndex = 0

    override var isFinished: Boolean = false
        private set

    override fun start() {
        isFinished = false
        targetDateString = getTargetDate(service)
        currentState = State.SEARCHING_HISTORI
        Log.d(TAG, "Starting UpdateDataWorkflow! Target Date: $targetDateString. Waiting 5s...")
        
        handler.postDelayed({
            triggerSearch()
        }, 5000)
    }

    private fun getTargetDate(context: Context): String {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("target_date", "") ?: ""
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
                Log.d(TAG, "Searching for 'Update Data' button near the date...")
                val dateNode = rootNode.findNodeByContentDescription(targetDateString)
                val scrollNode = rootNode.findScrollView()
                
                if (dateNode != null) {
                    val updateButton = dateNode.findNearbyNode("Update Data")
                    var isButtonVisible = false
                    
                    if (updateButton != null && scrollNode != null) {
                        val updateBounds = Rect()
                        val scrollBounds = Rect()
                        updateButton.getBoundsInScreen(updateBounds)
                        scrollNode.getBoundsInScreen(scrollBounds)
                        
                        if (updateBounds.top >= scrollBounds.top && updateBounds.bottom <= scrollBounds.bottom) {
                            isButtonVisible = true
                        }
                    }

                    if (updateButton != null && isButtonVisible && updateButton.isClickable) {
                        if (updateButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Log.d(TAG, "SUCCESS! Clicked the 'Update Data' button.")
                            foundInCurrentStep = true
                            currentState = State.VALIDATING_DETAIL_SUMMARY_PAGE
                        } else {
                            Log.d(TAG, "Update Data button found but click failed.")
                        }
                    } else {
                        Log.d(TAG, "Update Data button not fully visible or not found. Doing a small scroll...")
                        if (scrollNode != null) {
                            GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 500L)
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
                val currentProduct = productsToAdd.getOrNull(currentProductIndex)
                if (currentProduct == null) {
                    Log.d(TAG, "All products processed! Moving to CLICKING_LANJUT.")
                    currentState = State.CLICKING_LANJUT
                    triggerSearch()
                    return
                }
                
                val code = currentProduct.first
                Log.d(TAG, "Searching product code: $code")
                
                val searchBox = if (currentProductIndex == 0) {
                    rootNode.findNodeByHint("Cari nama produk")
                } else {
                    val prevCode = productsToAdd[currentProductIndex - 1].first
                    rootNode.findNodeByText(prevCode) ?: rootNode.findNodeByHint("Cari nama produk")
                }
                
                if (searchBox != null) {
                    searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val currentBox = if (currentProductIndex == 0) {
                            service.rootInActiveWindow?.findNodeByHint("Cari nama produk") ?: searchBox
                        } else {
                            val prevCode = productsToAdd[currentProductIndex - 1].first
                            service.rootInActiveWindow?.findNodeByText(prevCode) ?: searchBox
                        }
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
                val code = productsToAdd[currentProductIndex].first
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
                val code = productsToAdd[currentProductIndex].first
                val qty = productsToAdd[currentProductIndex].second
                Log.d(TAG, "Inputting quantity $qty for $code...")
                
                val qtyNode = rootNode.findNodeByText("0")
                if (qtyNode != null) {
                    qtyNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val currentQtyNode = service.rootInActiveWindow?.findNodeByText("0") ?: qtyNode
                        currentQtyNode.typeTextOneByOne(qty, handler) {
                            Log.d(TAG, "SUCCESS! Typed quantity $qty for $code.")
                            currentProductIndex++
                            currentState = State.SEARCHING_PRODUCT
                            triggerSearch()
                        }
                    }, 500)
                    currentState = State.TYPING_IN_PROGRESS
                } else {
                    Log.d(TAG, "EditText with text '0' not found. It might be that the text changed or hasn't rendered.")
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
                    currentState = State.GATHERING_SUMMARY_INFO
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
                    Log.d(TAG, "Could not find Summary Info. Still searching...")
                }
            }
            
            State.SCROLL_TO_SUBMIT -> {
                Log.d(TAG, "Searching for Submit Sellout button...")
                val submitNode = rootNode.findNodeByContentDescription("Submit Sellout")
                if (submitNode != null) {
                    if (submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Submit Sellout.")
                        foundInCurrentStep = true
                        currentState = State.INPUT_RECEIPT_COUNT
                    }
                } else {
                    Log.d(TAG, "Submit Sellout not found. Scrolling down...")
                    val scrollNode = rootNode.findScrollView()
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.40f, 800L, true)
                    }
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
                Log.d(TAG, "Ready to click 'Submit Struk', but it is commented out for now. WORKFLOW COMPLETE.")
                /*
                val submitStruk = rootNode.findNodeByContentDescription("Submit Struk")
                if (submitStruk != null) {
                    submitStruk.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                */
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
                State.WAITING_SEARCH_RESULT
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
        handler.postDelayed({
            triggerSearch()
        }, 2000)
    }

    override fun cancel() {
        currentState = State.IDLE
        isFinished = true
        handler.removeCallbacksAndMessages(null)
    }
}
