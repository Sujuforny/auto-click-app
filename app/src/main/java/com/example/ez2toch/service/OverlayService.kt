package com.example.ez2toch.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    
    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_channel"
        
        fun startOverlay(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopOverlay(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService started")
        showFloatingButton()
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed")
        hideFloatingButton()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating stop button for auto clicker"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Clicker Active")
            .setContentText("Floating stop button is visible")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun showFloatingButton() {
        if (floatingView != null) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Create the floating button layout
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        
        // Position the button
        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = 20
        layoutParams.y = 100
        
        // Create the button
        val button = Button(this).apply {
            text = "STOP"
            setBackgroundColor(0xFFFF4444.toInt()) // Red background
            setTextColor(0xFFFFFFFF.toInt()) // White text
            textSize = 12f
            setPadding(20, 10, 20, 10)
            
            setOnClickListener {
                Log.d(TAG, "Stop button clicked")
                AutoClickerService.stopClicking()
                stopSelf()
            }
        }
        
        // Create container layout
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button)
        }
        
        floatingView = container
        
        try {
            windowManager?.addView(floatingView, layoutParams)
            Log.d(TAG, "Floating button added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button: ${e.message}")
        }
    }
    
    private fun hideFloatingButton() {
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Floating button removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button: ${e.message}")
            }
        }
        floatingView = null
    }
}
