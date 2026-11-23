package com.antigravity.reellimiter.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.antigravity.reellimiter.R
import com.antigravity.reellimiter.data.PreferenceManager

class ReelAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferenceManager
    private val handler = Handler(Looper.getMainLooper())
    private var isWatchingReel = false
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lastBlockTimestamp = 0L

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (isWatchingReel && prefs.isServiceEnabled) {
                prefs.addSeconds(1)

                if (prefs.isLimitReached) {
                    blockReels()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferenceManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("ReelLimiter", "ReelAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::prefs.isInitialized || !prefs.isServiceEnabled) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.instagram.android") {
            stopReelTimer()
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val reelsDetected = detectReelsView(rootNode)

        if (reelsDetected) {
            if (prefs.isLimitReached) {
                blockReels()
            } else if (!isWatchingReel) {
                startReelTimer()
            }
        } else {
            stopReelTimer()
        }
    }

    private fun detectReelsView(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Direct view ID checks commonly used by Instagram for Reels containers/viewers
        val reelViewIds = listOf(
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_swipe_refresh_layout"
        )

        for (id in reelViewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }

        // 2. Check for navigation tab with description "Reels"
        val reelsTabNodes = rootNode.findAccessibilityNodeInfosByText("Reels")
        if (!reelsTabNodes.isNullOrEmpty()) {
            for (node in reelsTabNodes) {
                if (node.isSelected || node.parent?.isSelected == true) {
                    return true
                }
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (desc.contains("reels, tab") || desc.contains("reels tab")) {
                    return true
                }
            }
        }

        // 3. Recursive inspection for clips/reels identifiers
        return hasClipsOrReelMarkers(rootNode, depth = 0)
    }

    private fun hasClipsOrReelMarkers(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 6) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("clips_video") || viewId.contains("clips_viewer")) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasClipsOrReelMarkers(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun startReelTimer() {
        isWatchingReel = true
        handler.removeCallbacks(tickerRunnable)
        handler.postDelayed(tickerRunnable, 1000)
    }

    private fun stopReelTimer() {
        if (isWatchingReel) {
            isWatchingReel = false
            handler.removeCallbacks(tickerRunnable)
        }
    }

    private fun blockReels() {
        stopReelTimer()

        // Debounce block actions by at least 1.2 seconds so rapid back actions don't exit Instagram completely
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < 1200L) {
            return
        }
        lastBlockTimestamp = now

        val rootNode = rootInActiveWindow

        // Strategy 1: Switch directly to Feed or Home tab if accessible
        var navigatedAway = false
        if (rootNode != null) {
            navigatedAway = navigateToFeedTab(rootNode)
        }

        // Strategy 2: If we couldn't click Home/Feed, perform a single GLOBAL_ACTION_BACK to exit Reels viewer
        if (!navigatedAway) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        vibratePhone()
        showBlockOverlay()
    }

    private fun navigateToFeedTab(rootNode: AccessibilityNodeInfo): Boolean {
        // Try to find and click the Home / Feed tab
        val homeNodes = rootNode.findAccessibilityNodeInfosByText("Home")
        if (!homeNodes.isNullOrEmpty()) {
            for (node in homeNodes) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        val feedNodes = rootNode.findAccessibilityNodeInfosByText("Feed")
        if (!feedNodes.isNullOrEmpty()) {
            for (node in feedNodes) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        // Search by content description for Home/Feed
        return searchAndClickHome(rootNode, depth = 0)
    }

    private fun searchAndClickHome(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 6) return false

        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("home, tab") || desc.contains("home tab") || desc == "home") {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (searchAndClickHome(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val parent = node.parent
        if (parent != null && parent.isClickable) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    private fun vibratePhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {
            Log.e("ReelLimiter", "Vibrate failed", e)
        }
    }

    @SuppressLint("InflateParams")
    private fun showBlockOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 100
            }

            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_reel_blocked, null)

            val tvMessage = view.findViewById<TextView>(R.id.tvOverlayMessage)
            val btnDismiss = view.findViewById<Button>(R.id.btnOverlayDismiss)

            if (prefs.blockCompletely) {
                tvMessage.text = "Reels are permanently blocked. Your DMs, Feed, and Stories remain available!"
            } else {
                tvMessage.text = "Daily Reels limit reached (${prefs.dailyLimitMinutes}m). Your DMs, Feed, and Stories remain available!"
            }

            btnDismiss.setOnClickListener {
                dismissBlockOverlay()
            }

            windowManager?.addView(view, params)
            overlayView = view

            // Auto-dismiss after 4 seconds
            handler.postDelayed({
                dismissBlockOverlay()
            }, 4000)
        } catch (e: Exception) {
            Log.e("ReelLimiter", "Error showing overlay", e)
        }
    }

    private fun dismissBlockOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("ReelLimiter", "Error removing overlay", e)
        }
    }

    override fun onInterrupt() {
        stopReelTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReelTimer()
        dismissBlockOverlay()
    }
}
