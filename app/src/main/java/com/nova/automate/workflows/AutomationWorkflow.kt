package com.nova.automate.workflows

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Defines a general contract for any accessibility automation workflow.
 */
abstract class AutomationWorkflow(protected val service: AccessibilityService) {

    /**
     * Called when the accessibility service first detects the target app has been opened
     * and this workflow is initiated.
     */
    abstract fun start()

    /**
     * Called on each AccessibilityEvent if the workflow is active.
     * @param rootNode The root AccessibilityNodeInfo of the active window.
     */
    abstract fun process(rootNode: AccessibilityNodeInfo?)

    /**
     * Called to stop the workflow and clean up any handlers or resources.
     */
    abstract fun cancel()

    /**
     * True if the workflow has completed its task successfully.
     */
    abstract val isFinished: Boolean
}
