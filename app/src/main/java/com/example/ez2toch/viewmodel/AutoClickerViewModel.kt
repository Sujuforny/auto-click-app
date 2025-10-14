package com.example.ez2toch.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
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
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return enabledServices?.contains("${context.packageName}/${AutoClickerService::class.java.name}") == true
    }
}
