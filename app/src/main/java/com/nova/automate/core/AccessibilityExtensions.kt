package com.nova.automate.core

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "NovaAutomation"

/**
 * Searches for a node whose exact content description matches [targetDesc].
 */
fun AccessibilityNodeInfo.findNodeByContentDescription(targetDesc: String): AccessibilityNodeInfo? {
    if (this.contentDescription?.toString() == targetDesc) {
        return AccessibilityNodeInfo.obtain(this)
    }
    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findNodeByContentDescription(targetDesc)
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Searches for a node whose exact text matches [targetText].
 */
fun AccessibilityNodeInfo.findNodeByText(targetText: String): AccessibilityNodeInfo? {
    if (this.text?.toString() == targetText) {
        return AccessibilityNodeInfo.obtain(this)
    }
    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findNodeByText(targetText)
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Searches for a node whose hint text matches [targetHint].
 */
fun AccessibilityNodeInfo.findNodeByHint(targetHint: String): AccessibilityNodeInfo? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        if (this.hintText?.toString() == targetHint) {
            return AccessibilityNodeInfo.obtain(this)
        }
    }
    if (this.text?.toString() == targetHint) {
        return AccessibilityNodeInfo.obtain(this)
    }
    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findNodeByHint(targetHint)
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Sets text of an editable node using ACTION_SET_TEXT.
 */
fun AccessibilityNodeInfo.setInputText(text: String): Boolean {
    val arguments = android.os.Bundle()
    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    return this.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
}

/**
 * Pastes text into an editable node by setting the clipboard and calling ACTION_PASTE.
 * This is often more reliable for triggering text watchers and UI updates.
 */
fun AccessibilityNodeInfo.pasteText(context: android.content.Context, text: String): Boolean {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("automation", text)
    clipboard.setPrimaryClip(clip)
    return this.performAction(AccessibilityNodeInfo.ACTION_PASTE)
}

/**
 * Types text digit-by-digit to emulate human typing.
 */
fun AccessibilityNodeInfo.typeTextOneByOne(
    textToType: String,
    handler: android.os.Handler,
    delayBetweenChars: Long = 100L,
    onComplete: () -> Unit
) {
    if (textToType.isEmpty()) {
        onComplete()
        return
    }
    
    var index = 1
    val node = this
    
    val runnable = object : Runnable {
        override fun run() {
            val currentText = textToType.substring(0, index)
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            index++
            if (index <= textToType.length) {
                handler.postDelayed(this, delayBetweenChars)
            } else {
                handler.postDelayed({ onComplete() }, delayBetweenChars)
            }
        }
    }
    handler.post(runnable)
}

/**
 * Searches for a node whose text or content description contains [target].
 */
fun AccessibilityNodeInfo.findNodeByContentDescOrText(target: String): AccessibilityNodeInfo? {
    val desc = this.contentDescription?.toString()
    val text = this.text?.toString()

    if ((desc != null && desc.contains(target, ignoreCase = true)) ||
        (text != null && text.contains(target, ignoreCase = true))) {
        return AccessibilityNodeInfo.obtain(this)
    }

    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findNodeByContentDescOrText(target)
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Looks up to 3 levels up the hierarchy to find a node with [target].
 */
fun AccessibilityNodeInfo.findNearbyNode(target: String): AccessibilityNodeInfo? {
    var parent = this.parent
    var depth = 0
    while (parent != null && depth < 3) {
        val targetNode = parent.findNodeByContentDescOrText(target)
        if (targetNode != null) {
            parent.recycle()
            return targetNode
        }
        val oldParent = parent
        parent = parent.parent
        oldParent.recycle()
        depth++
    }
    return null
}

/**
 * Searches for a node whose exact className matches [targetClassName].
 */
fun AccessibilityNodeInfo.findNodeByClassName(targetClassName: String): AccessibilityNodeInfo? {
    if (this.className?.toString() == targetClassName) {
        return AccessibilityNodeInfo.obtain(this)
    }
    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findNodeByClassName(targetClassName)
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Finds a scrollable view.
 */
fun AccessibilityNodeInfo.findScrollView(): AccessibilityNodeInfo? {
    if (this.isScrollable) {
        return AccessibilityNodeInfo.obtain(this)
    }
    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            val result = child.findScrollView()
            child.recycle()
            if (result != null) return result
        }
    }
    return null
}

/**
 * Helper to dump the screen tree to logs.
 */
fun AccessibilityNodeInfo.logAllScreenElements() {
    val text = this.text?.toString()
    val desc = this.contentDescription?.toString()
    val id = this.viewIdResourceName
    val clazz = this.className
    
    if (!text.isNullOrBlank() || !desc.isNullOrBlank() || id != null) {
        var hint = ""
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            hint = this.hintText?.toString() ?: ""
        }
        Log.d(TAG, "UI Node | Class: $clazz | ID: $id | Text: '$text' | Hint: '$hint' | Desc: '$desc' | Clickable: ${this.isClickable}")
    }

    for (i in 0 until this.childCount) {
        val child = this.getChild(i)
        if (child != null) {
            child.logAllScreenElements()
            child.recycle()
        }
    }
}

/**
 * Finds the brand dropdown on the Sellout page.
 *
 * The dropdown is labelled with whatever brand happens to be selected ("OMG", "Emina", ...),
 * so its content description can't be matched directly. What stays stable is its shape and
 * placement: a plain clickable view sitting just below the "Riwayat Sellout" link in the top
 * strip of the screen, carrying a short content description, and laid out as a wide pill
 * rather than an icon.
 */
fun AccessibilityNodeInfo.findBrandDropdown(): AccessibilityNodeInfo? {
    val screenBounds = Rect()
    this.getBoundsInScreen(screenBounds)
    val topStripBottom = if (screenBounds.height() > 0) {
        screenBounds.top + (screenBounds.height() * 0.25f).toInt()
    } else {
        Int.MAX_VALUE
    }

    // The dropdown sits below the "Riwayat Sellout" link, which is itself a clickable pill in
    // the same corner of the header. Use it as the anchor so it can never be picked instead.
    var searchTop = screenBounds.top
    val riwayatNode = this.findNodeByContentDescription("Riwayat Sellout")
    if (riwayatNode != null) {
        val riwayatBounds = Rect()
        riwayatNode.getBoundsInScreen(riwayatBounds)
        searchTop = riwayatBounds.bottom
        riwayatNode.recycle()
        Log.d(TAG, "Anchored brand dropdown search below 'Riwayat Sellout' (y=$searchTop).")
    }

    val ignoredDescriptions = listOf(
        "sellout", "update sellout", "riwayat sellout",
        "kembali", "back", "navigate up", "tutup", "close"
    )

    var best: AccessibilityNodeInfo? = null
    var bestBounds: Rect? = null

    fun scan(node: AccessibilityNodeInfo) {
        val desc = node.contentDescription?.toString()?.trim()
        val className = node.className?.toString() ?: ""
        val isIcon = className.contains("ImageView") || className.contains("ImageButton")
        if (node.isClickable && node.isVisibleToUser && !isIcon &&
            !desc.isNullOrEmpty() && desc.length <= 30 &&
            !ignoredDescriptions.contains(desc.lowercase())
        ) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val isPillShaped = bounds.width() >= bounds.height() * 1.5
            if (bounds.top >= searchTop && bounds.top <= topStripBottom && isPillShaped) {
                val currentBest = bestBounds
                val isBetter = currentBest == null ||
                    bounds.top < currentBest.top ||
                    (bounds.top == currentBest.top && bounds.left > currentBest.left)
                if (isBetter) {
                    Log.d(TAG, "Brand dropdown candidate: '$desc' ($className) at $bounds")
                    best?.recycle()
                    best = AccessibilityNodeInfo.obtain(node)
                    bestBounds = bounds
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                scan(child)
                child.recycle()
            }
        }
    }
    scan(this)

    val picked = best
    if (picked != null) {
        Log.d(TAG, "Brand dropdown picked: '${picked.contentDescription}'")
    } else {
        Log.d(TAG, "No brand dropdown found in the top strip of the screen.")
    }
    return picked
}
