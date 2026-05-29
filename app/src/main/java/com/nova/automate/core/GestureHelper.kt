package com.nova.automate.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object GestureHelper {

    /**
     * Performs a vertical swipe relative to the given node's bounds.
     * @param service The AccessibilityService to dispatch the gesture.
     * @param node The node defining the bounds to swipe inside.
     * @param distanceFraction Percentage of the node's height to swipe (e.g., 0.40f for 40%).
     * @param duration Duration of the swipe in milliseconds (e.g., 800L for smooth).
     */
    fun performCustomScroll(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        distanceFraction: Float,
        duration: Long,
        scrollDownList: Boolean = true
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val startX = bounds.centerX().toFloat()
        
        val startY: Float
        val endY: Float
        if (scrollDownList) {
            // Swiping up means starting from the bottom part to the top part.
            startY = bounds.centerY() + (bounds.height() * distanceFraction / 2)
            endY = bounds.centerY() - (bounds.height() * distanceFraction / 2)
        } else {
            // Swiping down means starting from the top part to the bottom part.
            startY = bounds.centerY() - (bounds.height() * distanceFraction / 2)
            endY = bounds.centerY() + (bounds.height() * distanceFraction / 2)
        }

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))

        service.dispatchGesture(gestureBuilder.build(), null, null)
    }
}
