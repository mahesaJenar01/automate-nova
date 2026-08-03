package com.nova.automate.core

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
