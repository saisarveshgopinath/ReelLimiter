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

        // Only react to state and content changes
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        // 1. NEVER block if user is currently inside Direct Messages (DMs) or Chat Thread
        if (isInDirectMessages(rootNode)) {
            stopReelTimer()
            return
        }

        // 2. Check if user is in Reels
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

    /**
     * Accurately detects if the active window is Direct Messages (Inbox or Chat thread).
     * If true, Reels blocking must NEVER trigger.
     */
    private fun isInDirectMessages(rootNode: AccessibilityNodeInfo): Boolean {
        // Common view IDs for Direct Messages / Thread in Instagram
        val directViewIds = listOf(
            "com.instagram.android:id/direct_thread_layout",
            "com.instagram.android:id/row_thread_composer_textarea",
            "com.instagram.android:id/direct_inbox_layout",
            "com.instagram.android:id/message_composer_container",
            "com.instagram.android:id/direct_text_message_text_view",
            "com.instagram.android:id/action_bar_title_chevron",
            "com.instagram.android:id/row_inbox_container"
        )
        for (id in directViewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }

        // Check for text identifiers like "Messages", "Chats", "Search chats"
        val messageHeaders = listOf("Messages", "Chats", "Direct", "Search")
        for (title in messageHeaders) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(title)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val resName = node.viewIdResourceName?.lowercase() ?: ""
                    if (resName.contains("title") || resName.contains("action_bar") || resName.contains("header")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Accurately detects if active screen is specifically the dedicated Reels viewer/tab.
     */
    private fun detectReelsView(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Check dedicated fullscreen clips viewer (when playing a Reel)
        val reelViewIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/reel_viewer_root"
        )

        for (id in reelViewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                return true
            }
        }

        // 2. Check if the bottom navigation Reels tab is CURRENTLY SELECTED
        val reelsTabNodes = rootNode.findAccessibilityNodeInfosByText("Reels")
        if (!reelsTabNodes.isNullOrEmpty()) {
            for (node in reelsTabNodes) {
                if (node.isSelected || node.parent?.isSelected == true) {
                    return true
                }
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if ((desc.contains("reels, tab") || desc.contains("reels tab")) && 
                    (node.isSelected || node.parent?.isSelected == true)) {
                    return true
                }
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

        val now = System.currentTimeMillis()
        // Stronger debounce (1.5 seconds) to stop any rapid-fire looping
        if (now - lastBlockTimestamp < 1500L) {
            return
        }
        lastBlockTimestamp = now

        val rootNode = rootInActiveWindow ?: return

        // 1. If we are on the Reels tab, click Home/Feed once
        val clickedHome = clickHomeTabIfSelected(rootNode)
        
        // 2. If it was a fullscreen reel opened over another screen, single Back action
        if (!clickedHome) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        vibratePhone()
        showBlockOverlay()
    }

    private fun clickHomeTabIfSelected(rootNode: AccessibilityNodeInfo): Boolean {
        // Look for the Home bottom navigation tab
        val homeNodes = rootNode.findAccessibilityNodeInfosByText("Home")
        if (!homeNodes.isNullOrEmpty()) {
            for (node in homeNodes) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }

        // Fallback: search by content description for Home tab
        return searchAndClickHome(rootNode, depth = 0)
    }

    private fun searchAndClickHome(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 5) return false

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
                tvMessage.text = "Reels are blocked. Your DMs, Feed, and Stories remain available!"
            } else {
                tvMessage.text = "Daily Reels limit reached (${prefs.dailyLimitMinutes}m). Your DMs and Feed remain available!"
            }

            btnDismiss.setOnClickListener {
                dismissBlockOverlay()
            }

            windowManager?.addView(view, params)
            overlayView = view

            // Auto-dismiss after 3 seconds
            handler.postDelayed({
                dismissBlockOverlay()
            }, 3000)
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
