package com.nova.automate.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nova.automate.workflows.AutomationWorkflow
import com.nova.automate.workflows.UpdateDataWorkflow

class NovaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NovaAutomation"
    }

    private var currentWorkflow: AutomationWorkflow? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val activePackage = rootInActiveWindow?.packageName?.toString() ?: event.packageName?.toString()

        if (activePackage == "com.pti.nova") {
            // If we don't have an active workflow, initialize one.
            if (currentWorkflow == null || currentWorkflow?.isFinished == true) {
                Log.d(TAG, "Entered target app. Initializing UpdateDataWorkflow...")
                currentWorkflow = UpdateDataWorkflow(this)
                currentWorkflow?.start()
            }
            
            // Pass the event to the workflow in case it needs to process it directly.
            currentWorkflow?.process(rootInActiveWindow)
            
        } else if (activePackage != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // We left the target app. Clean up active workflows.
            if (currentWorkflow != null) {
                Log.d(TAG, "Left target app (activePackage: $activePackage). Cancelling active workflow.")
                currentWorkflow?.cancel()
                currentWorkflow = null
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility interrupted")
        currentWorkflow?.cancel()
        currentWorkflow = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}