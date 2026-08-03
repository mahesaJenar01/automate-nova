package com.nova.automate.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nova.automate.workflows.AutomationWorkflow
import com.nova.automate.workflows.UpdateDataWorkflow

class NovaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NovaAutomation"
        // A run request is only honoured shortly after "Run Automation" was pressed.
        private const val REQUEST_VALIDITY_MS = 5 * 60 * 1000L
    }

    private var currentWorkflow: AutomationWorkflow? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val activePackage = rootInActiveWindow?.packageName?.toString() ?: event.packageName?.toString()

        if (activePackage == "com.pti.nova") {
            if (currentWorkflow?.isFinished == true) {
                currentWorkflow = null
            }

            // Only start a workflow when the user asked for one from Automate Nova.
            // Opening the target app by hand must never trigger the automation.
            if (currentWorkflow == null && consumeAutomationRequest()) {
                Log.d(TAG, "Entered target app with a pending run request. Initializing workflow...")
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

    /**
     * Returns true at most once per "Run Automation" press. The flag is cleared as soon as it is
     * read, so a request can never be replayed by re-entering the target app later on.
     */
    private fun consumeAutomationRequest(): Boolean {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("automation_requested", false)) return false

        prefs.edit().putBoolean("automation_requested", false).apply()

        val age = System.currentTimeMillis() - prefs.getLong("automation_requested_at", 0L)
        if (age > REQUEST_VALIDITY_MS) {
            Log.d(TAG, "Ignoring stale run request (${age}ms old).")
            return false
        }
        return true
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