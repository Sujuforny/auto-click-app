package com.example.ez2toch.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez2toch.service.AutoClickerService
import com.example.ez2toch.service.OverlayService
import com.example.ez2toch.data.AutoClickerUiState
import com.example.ez2toch.data.ClickSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                // Check if root access is available
                if (!isRootAvailable()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Root access not available. Please ensure device is rooted."
                    )
                    return@launch
                }
                
                // Check if screencap command is available
                if (!checkScreencapAvailable()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Screencap command not found. Please ensure Android screencap binary is available."
                    )
                    return@launch
                }
                
                // Use root access to take screenshot to internal storage
                val screenshotPath = "${context.filesDir.absolutePath}/screenshot_temp.png"
                val command = "screencap -p $screenshotPath"
                
                // Execute root command
                val process = Runtime.getRuntime().exec("su -c $command")
                val exitCode = process.waitFor()
                
                // Get error output for debugging
                val errorStream = process.errorStream
                val errorOutput = errorStream.bufferedReader().readText()
                
                if (exitCode == 0) {
                    // Read the screenshot file
                    val screenshotFile = File(screenshotPath)
                    if (screenshotFile.exists()) {
                        // Set proper permissions for the file
                        try {
                            Runtime.getRuntime().exec("su -c chmod 644 $screenshotPath").waitFor()
                        } catch (e: Exception) {
                            // Ignore permission setting errors
                        }
                        
                        val bitmap = BitmapFactory.decodeFile(screenshotPath)
                        if (bitmap != null) {
                            // Save to Downloads folder
                            saveScreenshotToDownloads(bitmap)
                            // Clean up temp file
                            screenshotFile.delete()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "Failed to decode screenshot. File may be corrupted."
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Screenshot file not found at: $screenshotPath"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Root access denied or screencap failed. Error: $errorOutput"
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Screenshot failed: ${e.message}"
                )
            }
        }
    }
    
    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    private fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c echo test")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkScreencapAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c which screencap")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun saveScreenshotToDownloads(bitmap: Bitmap) {
        try {
            // Create filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "screenshot_$timestamp.png"
            
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, filename)
            
            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Screenshot saved: $filename"
            )
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to save screenshot: ${e.message}"
            )
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return enabledServices?.contains("${context.packageName}/${AutoClickerService::class.java.name}") == true
    }
}
