package com.example.ez2toch.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez2toch.service.AutoClickerService
import com.example.ez2toch.service.OverlayService
import com.example.ez2toch.service.ScreenshotService
import com.example.ez2toch.service.ScreenshotCallback
import com.example.ez2toch.service.ColorAnalysisCallback
import com.example.ez2toch.service.ColorAnalysisData
import com.example.ez2toch.service.PixelColorCallback
import com.example.ez2toch.service.PixelColorData
import com.example.ez2toch.data.AutoClickerUiState
import com.example.ez2toch.data.ClickSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException

class AutoClickerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(AutoClickerUiState())
    val uiState: StateFlow<AutoClickerUiState> = _uiState.asStateFlow()
    
    private val context: Context = application.applicationContext
    
    init {
        startStatusMonitoring()
        setupServiceCallback()
    }
    
    private fun startStatusMonitoring() {
        viewModelScope.launch {
            while (true) {
                updateServiceStatus()
                updateOverlayPermission()
                updateClickingStatus()
                delay(1000)
            }
        }
    }
    
    private fun setupServiceCallback() {
        AutoClickerService.setStatusChangeCallback {
            updateClickingStatus()
        }
    }
    
    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        _uiState.value = _uiState.value.copy(isServiceEnabled = isEnabled)
    }
    
    private fun updateOverlayPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(hasOverlayPermission = hasPermission)
    }
    
    private fun updateClickingStatus() {
        val isClicking = AutoClickerService.isClicking()
        _uiState.value = _uiState.value.copy(isClicking = isClicking)
    }
    
    fun updateXCoordinate(x: String) {
        _uiState.value = _uiState.value.copy(xCoordinate = x)
    }
    
    fun updateYCoordinate(y: String) {
        _uiState.value = _uiState.value.copy(yCoordinate = y)
    }
    
    fun updateInterval(interval: String) {
        _uiState.value = _uiState.value.copy(intervalMs = interval)
    }
    
    fun updateCommandText(text: String) {
        _uiState.value = _uiState.value.copy(commandText = text)
    }
    
    fun executeCommandText() {
        val currentState = _uiState.value
        if (currentState.isServiceEnabled && !currentState.isClicking && currentState.commandText.isNotBlank()) {
            _uiState.value = currentState.copy(isLoading = true)
            
            try {
                // Create a temporary file with the command text
                val tempFile = File(context.filesDir, "temp_commands.txt")
                FileWriter(tempFile).use { writer ->
                    writer.write(currentState.commandText)
                }
                println("Root======>: ${AutoClickerService.checkRoot()}")
                AutoClickerService.executeCommandFile(tempFile.absolutePath)
                
                // Start overlay service if permission is granted
                if (currentState.hasOverlayPermission) {
                    OverlayService.startOverlay(context)
                }
                
                _uiState.value = currentState.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to execute commands"
                )
            }
        }
    }
    
    fun startClicking() {
        val currentState = _uiState.value
        if (currentState.isServiceEnabled && !currentState.isClicking) {
            _uiState.value = currentState.copy(isLoading = true)
            
            try {
                val settings = ClickSettings.fromStrings(
                    currentState.xCoordinate,
                    currentState.yCoordinate,
                    currentState.intervalMs
                )
                
                AutoClickerService.startClicking(settings.x, settings.y, settings.intervalMs)
                
                // Start overlay service if permission is granted
                if (currentState.hasOverlayPermission) {
                    OverlayService.startOverlay(context)
                }
                
                _uiState.value = currentState.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to start clicking"
                )
            }
        }
    }
    
    fun stopClicking() {
        val currentState = _uiState.value
        if (currentState.isClicking) {
            _uiState.value = currentState.copy(isLoading = true)
            
            try {
                AutoClickerService.stopClicking()
                OverlayService.stopOverlay(context)
                _uiState.value = currentState.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to stop clicking"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun executeCommandFile(filePath: String) {
        val currentState = _uiState.value
        if (currentState.isServiceEnabled && !currentState.isClicking) {
            _uiState.value = currentState.copy(isLoading = true)
            
            try {
                AutoClickerService.executeCommandFile(filePath)
                
                // Start overlay service if permission is granted
                if (currentState.hasOverlayPermission) {
                    OverlayService.startOverlay(context)
                }
                
                _uiState.value = currentState.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to execute command file"
                )
            }
        }
    }
    
    
    fun createConditionalSampleFile(): String {
        return try {
            // Read from assets
            val inputStream = context.assets.open("conditional_sample.txt")
            val content = inputStream.bufferedReader().use { it.readText() }
            
            // Write to internal storage
            val file = File(context.filesDir, "conditional_sample.txt")
            FileWriter(file).use { writer ->
                writer.write(content)
            }
            file.absolutePath
        } catch (e: IOException) {
            throw Exception("Failed to create conditional sample file: " + e.message)
        }
    }
    
    fun getCommandFilesDirectory(): String {
        return context.filesDir.absolutePath
    }
    
    fun takeScreenshotWithRoot() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        ScreenshotService.getInstance().takeScreenshotWithRoot(context, object : ScreenshotCallback {
            override fun onScreenshotSuccess(filename: String) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Screenshot saved: $filename"
                )
            }
            
            override fun onScreenshotError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error
                )
            }
            
            override fun onScreenshotProgress(message: String) {
                // Update progress if needed
                // For now, we'll just keep the loading state
            }
        })
    }
    
    fun getPixelColorAt(x: Int, y: Int, saveScreenshot: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        ScreenshotService.getInstance().getPixelColorAt(context, x, y, saveScreenshot, object : PixelColorCallback {
            override fun onPixelColorSuccess(pixelData: PixelColorData) {
                val message = buildString {
                    append("🎯 Pixel Color at ($x, $y):\n\n")
                    append("Color: ${pixelData.hex}\n")
                    append("RGB: (${pixelData.rgb.first}, ${pixelData.rgb.second}, ${pixelData.rgb.third})\n")
                    append("Alpha: ${pixelData.alpha}\n")
                    append("Brightness: ${(pixelData.brightness * 100).toInt()}%\n")
                    if (pixelData.screenshotSaved && pixelData.filename != null) {
                        append("\nScreenshot saved: ${pixelData.filename}")
                    } else {
                        append("\nScreenshot not saved (pixel check only)")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = message
                )
            }
            
            override fun onPixelColorError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Pixel color detection failed: $error"
                )
            }
            
            override fun onPixelColorProgress(message: String) {
                // Update progress if needed
                // For now, we'll just keep the loading state
            }
        })
    }
    
    fun takeScreenshotAndAnalyzeColors(saveScreenshot: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        ScreenshotService.getInstance().takeScreenshotAndAnalyzeColors(context, saveScreenshot, object : ColorAnalysisCallback {
            override fun onColorAnalysisSuccess(colorData: ColorAnalysisData) {
                val message = buildString {
                    append("Color Analysis Complete!\n")
                    append("Dominant Color: ${colorData.dominantColorHex}\n")
                    append("Brightness: ${(colorData.brightness * 100).toInt()}%\n")
                    append("Contrast: ${(colorData.contrast * 100).toInt()}%\n")
                    if (colorData.screenshotSaved && colorData.filename != null) {
                        append("Screenshot saved: ${colorData.filename}")
                    } else {
                        append("Screenshot not saved (analysis only)")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = message
                )
            }
            
            override fun onColorAnalysisError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Color analysis failed: $error"
                )
            }
            
            override fun onColorAnalysisProgress(message: String) {
                // Update progress if needed
                // For now, we'll just keep the loading state
            }
        })
    }
    
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return enabledServices?.contains("${context.packageName}/${AutoClickerService::class.java.name}") == true
    }
}
