package com.example.ez2toch.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ez2toch.model.Command
import com.example.ez2toch.model.ExecutionContext
import com.example.ez2toch.parser.CommandParser
import java.io.File
import java.io.FileInputStream
import java.io.IOException

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
        
        fun executeCommandFile(filePath: String) {
            instance?.executeCommandFile(filePath)
        }
        fun checkRoot(): Boolean = instance?.checkRoot() ?: false
    }
    
    private var isClicking = false
    private var clickJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Overlay message system
    private var windowManager: WindowManager? = null
    private var messageOverlay: View? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "AutoClickerService connected")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAutoClick()
        serviceScope.cancel()
        hideMessageOverlay() // Clean up overlay
        Log.d(TAG, "AutoClickerService destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for this use case
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AutoClickerService interrupted")
    }
    
    fun startAutoClick(x: Int, y: Int, intervalMs: Long) {
        var isRoot = checkRoot();
        Log.d(TAG, "========= ${isRoot}===")
        if (isClicking) {
            stopAutoClick()
        }
        
        isClicking = true
        Log.d(TAG, "Starting auto click at ($x, $y) with interval ${intervalMs}ms")
        
        // Notify UI of status change
        statusChangeCallback?.invoke()
        
        clickJob = serviceScope.launch {
            while (isClicking) {
                delay(2000)
                performClick(x, y)
                delay(5000)
                isClicking = false
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

    fun executeCommandFile(filePath: String) {
        if (isClicking) {
            stopAutoClick()
        }
        
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Command file does not exist: $filePath")
                return
            }
            
            val content = file.readText()
            val commands = CommandParser.parseCommands(content)
            
            if (commands.isEmpty()) {
                Log.w(TAG, "No valid commands found in file: $filePath")
                return
            }
            
            // Validate commands
            val errors = CommandParser.validateCommands(commands)
            if (errors.isNotEmpty()) {
                Log.e(TAG, "Command validation errors: ${errors.joinToString(", ")}")
                return
            }
            
            isClicking = true
            Log.d(TAG, "Starting command sequence from file: $filePath")
            
            // Notify UI of status change
            statusChangeCallback?.invoke()
            
            clickJob = serviceScope.launch {
                val context = ExecutionContext()
                executeCommandSequence(commands, context)
                isClicking = false
                Log.d(TAG, "Command sequence completed")
                // Notify UI of status change
                statusChangeCallback?.invoke()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command file: ${e.message}")
            isClicking = false
            statusChangeCallback?.invoke()
        }
    }
    
    private suspend fun executeCommandSequence(commands: List<Command>, context: ExecutionContext) {
        var index = 0
        while (index < commands.size && isClicking) {
            val command = commands[index]
            
            when (command) {
                is Command.Click -> {
                    performClick(command.x, command.y)
                    delay(100) // Small delay between actions
                }
                
                is Command.Delay -> {
                    delay(command.milliseconds)
                }
                
                is Command.ThreeFingerTap -> {
                    executeThreeFingerTap(command.x, command.y)
                    delay(100)
                }
                
                is Command.Swipe -> {
                    executeSwipe(command.startX, command.startY, command.endX, command.endY, command.duration)
                    delay(100)
                }
                
                is Command.LongPress -> {
                    executeLongPress(command.x, command.y, command.duration)
                    delay(100)
                }
                
                is Command.Stop -> {
                    Log.d(TAG, "Stop command received")
                    stopAutoClick()
                    break
                }
                
                is Command.Comment -> {
                    Log.d(TAG, "Comment: ${command.text}")
                }
                
                is Command.If -> {
                    val conditionResult = CommandParser.evaluateCondition(command.condition, context)
                    Log.d(TAG, "If condition '${command.condition}' evaluated to: $conditionResult")
                    
                    if (conditionResult) {
                        executeCommandSequence(command.thenCommands, context)
                    } else if (command.elseCommands.isNotEmpty()) {
                        executeCommandSequence(command.elseCommands, context)
                    }
                }
                
                is Command.While -> {
                    Log.d(TAG, "Starting while loop with condition: ${command.condition}")
                    while (isClicking && CommandParser.evaluateCondition(command.condition, context)) {
                        executeCommandSequence(command.commands, context)
                        delay(100) // Small delay between iterations
                    }
                    Log.d(TAG, "While loop ended")
                }
                
                is Command.Repeat -> {
                    Log.d(TAG, "Starting repeat loop ${command.count} times")
                    repeat(command.count) { iteration ->
                        if (!isClicking) return@repeat
                        Log.d(TAG, "Repeat iteration ${iteration + 1}/${command.count}")
                        executeCommandSequence(command.commands, context)
                        delay(100) // Small delay between iterations
                    }
                    Log.d(TAG, "Repeat loop completed")
                }
                
                is Command.SetVariable -> {
                    context.setVariable(command.name, command.value)
                    Log.d(TAG, "Set variable '${command.name}' = '${command.value}'")
                }
                
                is Command.GetVariable -> {
                    val value = context.getVariable(command.name)
                    Log.d(TAG, "Get variable '${command.name}' = '$value'")
                }
                
                is Command.CheckColor -> {
                    // TODO: Implement color checking using accessibility service
                    Log.d(TAG, "CheckColor at (${command.x}, ${command.y}) for color '${command.expectedColor}' - Not implemented yet")
                }
                
                is Command.CheckText -> {
                    // TODO: Implement text checking using accessibility service
                    Log.d(TAG, "CheckText at (${command.x}, ${command.y}) for text '${command.expectedText}' - Not implemented yet")
                }
                
                is Command.Label -> {
                    context.setLabel(command.name, index)
                    Log.d(TAG, "Label '${command.name}' set at index $index")
                }
                
                is Command.Goto -> {
                    val labelIndex = context.getLabelIndex(command.labelName)
                    if (labelIndex != null) {
                        Log.d(TAG, "Goto to label '${command.labelName}' at index $labelIndex")
                        index = labelIndex - 1 // -1 because we'll increment at the end
                    } else {
                        Log.e(TAG, "Label '${command.labelName}' not found")
                    }
                }
                
                is Command.GotoIf -> {
                    val conditionResult = CommandParser.evaluateCondition(command.condition, context)
                    if (conditionResult) {
                        val labelIndex = context.getLabelIndex(command.labelName)
                        if (labelIndex != null) {
                            Log.d(TAG, "GotoIf condition '${command.condition}' true, jumping to '${command.labelName}' at index $labelIndex")
                            index = labelIndex - 1 // -1 because we'll increment at the end
                        } else {
                            Log.e(TAG, "Label '${command.labelName}' not found")
                        }
                    } else {
                        Log.d(TAG, "GotoIf condition '${command.condition}' false, continuing")
                    }
                }
                
                is Command.Log -> {
                    Log.i(TAG, "LOG: ${command.message}")
                }
                
                is Command.LogVariable -> {
                    val value = context.getVariable(command.variableName)
                    Log.i(TAG, "LOG VAR: ${command.variableName} = $value")
                }
                
                is Command.Logs -> {
                    Log.i(TAG, "LOGS: ${command.message}")
                    // Display message on screen for 3 seconds
                    showScreenMessage(command.message)
                }
                
                is Command.FunctionDef -> {
                    Log.i(TAG, "FUNCTION DEF: ${command.name}")
                    context.setFunction(command.name, command.commands)
                }
                
                is Command.FunctionCall -> {
                    Log.i(TAG, "FUNCTION CALL: ${command.name}")
                    val functionCommands = context.getFunction(command.name)
                    if (functionCommands != null) {
                        // Execute function commands recursively
                        executeCommandSequence(functionCommands, context)
                    } else {
                        Log.e(TAG, "Function '${command.name}' not found")
                    }
                }
            }
            
            index++
        }
    }
    
    private fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long) {
        try {
            val path = Path()
            path.moveTo(startX.toFloat(), startY.toFloat())
            path.lineTo(endX.toFloat(), endY.toFloat())
            
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            
            val gesture = gestureBuilder.build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Swipe performed from ($startX, $startY) to ($endX, $endY)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "Swipe cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe: ${e.message}")
        }
    }
    
    private fun executeLongPress(x: Int, y: Int, duration: Long) {
        try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            
            val gesture = gestureBuilder.build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Long press performed at ($x, $y) for ${duration}ms")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.d(TAG, "Long press cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing long press: ${e.message}")
        }
    }
    
    private fun showScreenMessage(message: String) {
        try {
            Log.d(TAG, "Screen message displayed: $message")
            
            // Remove existing overlay if any
            hideMessageOverlay()
            
            // Create new overlay
            createMessageOverlay(message)
            
            // Auto-hide after 3 seconds
            handler.postDelayed({
                hideMessageOverlay()
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing screen message: ${e.message}")
        }
    }
    
    private fun createMessageOverlay(message: String) {
        try {
            val textView = TextView(this).apply {
                text = message
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
                setPadding(20, 15, 20, 15)
                gravity = Gravity.CENTER
            }
            
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
            
            // Position at top center
            layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams.x = 0
            layoutParams.y = 100
            
            messageOverlay = textView
            windowManager?.addView(messageOverlay, layoutParams)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating message overlay: ${e.message}")
        }
    }
    
    private fun hideMessageOverlay() {
        messageOverlay?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
                Log.d(TAG, "Message overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing message overlay: ${e.message}")
            }
        }
        messageOverlay = null
    }

    fun checkRoot(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
}
