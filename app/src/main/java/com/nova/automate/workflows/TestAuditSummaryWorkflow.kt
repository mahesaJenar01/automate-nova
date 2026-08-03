package com.nova.automate.workflows

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.automate.core.*

class TestAuditSummaryWorkflow(service: AccessibilityService) : AutomationWorkflow(service) {

    companion object {
        private const val TAG = "NovaAutomation"
    }

    private enum class State {
        IDLE,
        WAITING_FOR_SUMMARY_PAGE,
        AUDIT_SUMMARY_PAGE,
        CLICKING_TAMBAH_PRODUK,
        WAITING_FOR_SELLOUT_PAGE,
        SEARCHING_PRODUCT,
        TYPING_IN_PROGRESS,
        WAITING_SEARCH_RESULT,
        INPUT_QUANTITY,
        CLICKING_LANJUT
    }

    private var currentState = State.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    
    override var isFinished: Boolean = false
        private set

    private val testProducts = mutableMapOf(
        "80277" to "1",
        "80099" to "2",
        "80098" to "3",
        "80100" to "2",
        "80246" to "2",
        "80247" to "1",
        "80248" to "1",
        "80249" to "1",
        "80250" to "1",
        "80251" to "1",
        "80252" to "1",
        "80253" to "1",
        "80369" to "1",
        "80370" to "1",
        "80371" to "1",
        "80372" to "1",
        "80373" to "1"
    )
    
    private val auditedProducts = mutableSetOf<String>()
    private var missingProductsList = listOf<String>()
    private var currentMissingIndex = 0

    override fun start() {
        isFinished = false
        currentState = State.WAITING_FOR_SUMMARY_PAGE
        auditedProducts.clear()
        missingProductsList = emptyList()
        currentMissingIndex = 0
        Log.d(TAG, "Starting TestAuditSummaryWorkflow! Waiting 5s...")
        scheduleSearch(5000)
    }

    override fun process(rootNode: AccessibilityNodeInfo?) {}

    private fun triggerSearch() {
        if (currentState == State.IDLE) {
            isFinished = true
            return
        }

        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            scheduleSearch(1000)
            return
        }

        var foundInCurrentStep = false

        when (currentState) {
            State.WAITING_FOR_SUMMARY_PAGE -> {
                Log.d(TAG, "Waiting for Summary page...")
                val summaryNode = rootNode.findNodeByContentDescription("Summary")
                if (summaryNode != null) {
                    Log.d(TAG, "SUCCESS! Reached Summary page. Starting audit.")
                    currentState = State.AUDIT_SUMMARY_PAGE
                    foundInCurrentStep = true
                }
            }
            State.AUDIT_SUMMARY_PAGE -> {
                Log.d(TAG, "Auditing Summary page...")
                
                fun scanNodeForProducts(node: AccessibilityNodeInfo) {
                    if (node.className == "android.widget.EditText") {
                        val qtyStr = node.text?.toString() ?: ""
                        val hintStr = node.hintText?.toString() ?: ""
                        if (hintStr.isNotEmpty()) {
                            val code = hintStr.substringBefore("\n").trim()
                            if (testProducts.containsKey(code)) {
                                val expectedQty = testProducts[code]
                                if (qtyStr == expectedQty) {
                                    if (!auditedProducts.contains(code)) {
                                        Log.d(TAG, "SUCCESS! Audited product: $code")
                                        auditedProducts.add(code)
                                    }
                                } else {
                                    Log.d(TAG, "WARNING! Quantity mismatch for $code. Expected $expectedQty, found $qtyStr. Will fix via Tambah Produk.")
                                }
                            }
                        }
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
                    Log.d(TAG, "Submit Sellout is visible. Finished scrolling.")
                    val missing = testProducts.keys.filter { !auditedProducts.contains(it) }
                    if (missing.isEmpty()) {
                        Log.d(TAG, "All test products audited and fixed successfully! Done.")
                        currentState = State.IDLE
                        isFinished = true
                        return
                    } else {
                        Log.d(TAG, "Missing products: $missing. Clicking Tambah Produk.")
                        missingProductsList = missing
                        currentMissingIndex = 0
                        currentState = State.CLICKING_TAMBAH_PRODUK
                        foundInCurrentStep = true
                    }
                } else {
                    Log.d(TAG, "Submit Sellout not visible yet. Scrolling down smoothly and slowly...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, true)
                        foundInCurrentStep = true
                    }
                }
            }
            State.CLICKING_TAMBAH_PRODUK -> {
                val tambahNode = rootNode.findNodeByContentDescOrText("Tambah Produk")
                if (tambahNode != null) {
                    var nodeToClick = tambahNode
                    while (nodeToClick != null && !nodeToClick.isClickable) {
                        nodeToClick = nodeToClick.parent
                    }
                    if (nodeToClick != null && nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Tambah Produk.")
                        currentState = State.WAITING_FOR_SELLOUT_PAGE
                        foundInCurrentStep = true
                    }
                } else {
                    Log.d(TAG, "Tambah Produk not found, scrolling up smoothly to find it...")
                    val scrollNode = rootNode.findNodeByClassName("android.widget.ScrollView") ?: rootNode.findScrollView() ?: rootNode
                    if (scrollNode != null) {
                        GestureHelper.performCustomScroll(service, scrollNode, 0.15f, 1000L, false)
                        foundInCurrentStep = true
                    }
                }
            }
            State.WAITING_FOR_SELLOUT_PAGE -> {
                Log.d(TAG, "Waiting for Sellout page (Cari nama produk)...")
                val searchBox = rootNode.findNodeByClassName("android.widget.EditText") ?: rootNode.findNodeByHint("Cari nama produk")
                if (searchBox != null) {
                    Log.d(TAG, "SUCCESS! Reached Sellout page.")
                    currentState = State.SEARCHING_PRODUCT
                    triggerSearch()
                }
            }
            State.SEARCHING_PRODUCT -> {
                if (currentMissingIndex >= missingProductsList.size) {
                    Log.d(TAG, "All missing products processed! Moving to CLICKING_LANJUT.")
                    currentState = State.CLICKING_LANJUT
                    triggerSearch()
                    return
                }
                
                val code = missingProductsList[currentMissingIndex]
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
                }
            }
            State.TYPING_IN_PROGRESS -> {
                Log.d(TAG, "Typing in progress... waiting.")
            }
            State.WAITING_SEARCH_RESULT -> {
                val code = missingProductsList[currentMissingIndex]
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
                val code = missingProductsList[currentMissingIndex]
                val qty = testProducts[code] ?: "1"
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
                            currentMissingIndex++
                            currentState = State.SEARCHING_PRODUCT
                            triggerSearch()
                        }
                    }, 500)
                    currentState = State.TYPING_IN_PROGRESS
                } else {
                    Log.d(TAG, "EditText for quantity not found.")
                }
            }
            State.CLICKING_LANJUT -> {
                Log.d(TAG, "Clicking Lanjut button...")
                val lanjutNode = rootNode.findNodeByContentDescription("Lanjut")
                if (lanjutNode != null) {
                    if (lanjutNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "SUCCESS! Clicked Lanjut. Looping back to WAIT FOR SUMMARY PAGE.")
                        foundInCurrentStep = true
                        currentState = State.WAITING_FOR_SUMMARY_PAGE
                    }
                }
            }
            else -> {}
        }

        if (foundInCurrentStep) {
            scheduleSearch(1500)
        } else {
            scheduleSearch(1000)
        }
    }

    private fun scheduleSearch(delayMs: Long) {
        searchRunnable?.let { handler.removeCallbacks(it) }
        searchRunnable = Runnable {
            if (!isFinished) triggerSearch()
        }
        handler.postDelayed(searchRunnable!!, delayMs)
    }

    override fun cancel() {
        Log.d(TAG, "TestAuditSummaryWorkflow cancelled.")
        isFinished = true
        searchRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
    }
}
