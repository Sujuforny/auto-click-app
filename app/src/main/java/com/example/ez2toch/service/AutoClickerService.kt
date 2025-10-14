package com.example.ez2toch.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class AutoClickerService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AutoClickerService"
        private var instance: AutoClickerService? = null
        private var statusChangeCallback: (() -> Unit)? = null
        
        fun getInstance(): AutoClickerService? = instance
        
        fun startClicking(x: Int, y: Int, intervalMs: Long) {
            instance?.startAutoClick(x, y, intervalMs)
        }
        
        fun stopClicking() {
            instance?.stopAutoClick()
        }
        
        fun isServiceRunning(): Boolean = instance != null
        
        fun isClicking(): Boolean = instance?.isClicking ?: false
        
        fun setStatusChangeCallback(callback: (() -> Unit)?) {
            statusChangeCallback = callback
        }
        
        fun performThreeFingerTap(x: Int, y: Int) {
            instance?.executeThreeFingerTap(x, y)
        }
    }
    
    private var isClicking = false
    private var clickJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoClickerService connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAutoClick()
        serviceScope.cancel()
        Log.d(TAG, "AutoClickerService destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for this use case
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AutoClickerService interrupted")
    }
    
    fun startAutoClick(x: Int, y: Int, intervalMs: Long) {
        if (isClicking) {
            stopAutoClick()
        }
        
        isClicking = true
        Log.d(TAG, "Starting auto click at ($x, $y) with interval ${intervalMs}ms")
        
        // Notify UI of status change
        statusChangeCallback?.invoke()
        
        clickJob = serviceScope.launch {
            while (isClicking) {
                performClick(x, y)
                delay(intervalMs)
            }
        }
    }
    
    fun stopAutoClick() {
        if (isClicking) {
            isClicking = false
            clickJob?.cancel()
            clickJob = null
            Log.d(TAG, "Stopped auto click")
            
            // Notify UI of status change
            statusChangeCallback?.invoke()
        }
    }
    
    private fun performClick(x: Int, y: Int) {
        try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            
            val gesture = gestureBuilder.build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Click performed at ($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "Click cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click: ${e.message}")
        }
    }
    
    private fun executeThreeFingerTap(x: Int, y: Int) {
        try {
            // Create three separate paths for three fingers
            val path1 = Path()
            path1.moveTo(x.toFloat(), y.toFloat())
            
            val path2 = Path()
            path2.moveTo((x + 20).toFloat(), (y + 20).toFloat())
            
            val path3 = Path()
            path3.moveTo((x - 20).toFloat(), (y + 20).toFloat())
            
            val gestureBuilder = GestureDescription.Builder()
            
            // Add three simultaneous strokes for three-finger tap
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path1, 0, 100))
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path2, 0, 100))
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path3, 0, 100))
            
            val gesture = gestureBuilder.build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Three-finger tap performed at ($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "Three-finger tap cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing three-finger tap: ${e.message}")
        }
    }
}
