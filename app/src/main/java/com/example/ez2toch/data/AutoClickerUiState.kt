package com.example.ez2toch.data

data class AutoClickerUiState(
    val xCoordinate: String = "500",
    val yCoordinate: String = "1000",
    val intervalMs: String = "1000",
    val isServiceEnabled: Boolean = false,
    val isClicking: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ClickSettings(
    val x: Int,
    val y: Int,
    val intervalMs: Long
) {
    companion object {
        fun fromStrings(x: String, y: String, interval: String): ClickSettings {
            return ClickSettings(
                x = x.toIntOrNull() ?: 500,
                y = y.toIntOrNull() ?: 1000,
                intervalMs = interval.toLongOrNull() ?: 1000L
            )
        }
    }
}
