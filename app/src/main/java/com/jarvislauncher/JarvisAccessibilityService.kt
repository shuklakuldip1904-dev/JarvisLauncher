package com.jarvislauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    @Volatile
    private var visibleText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text)
        visibleText = text.toString().take(MAX_CAPTURED_CHARACTERS)
        root.recycle()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    fun scroll(directionDown: Boolean): Boolean {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val startY = height * if (directionDown) 0.78f else 0.3f
        val endY = height * if (directionDown) 0.3f else 0.78f
        val path = Path().apply {
            moveTo(width * 0.5f, startY)
            lineTo(width * 0.5f, endY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
                .build(),
            null,
            null
        )
    }

    fun tap(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= resources.displayMetrics.widthPixels ||
            y >= resources.displayMetrics.heightPixels
        ) {
            return false
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(),
            null,
            null
        )
    }

    fun currentVisibleText(): String = visibleText

    private fun collectText(node: AccessibilityNodeInfo, result: StringBuilder) {
        node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
            if (result.isNotEmpty()) result.append('\n')
            result.append(it)
        }
        node.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let {
            if (result.isNotEmpty()) result.append('\n')
            result.append(it)
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectText(child, result)
                child.recycle()
            }
        }
    }

    companion object {
        private const val MAX_CAPTURED_CHARACTERS = 8_000

        @Volatile
        var activeService: JarvisAccessibilityService? = null
            private set
    }
}
