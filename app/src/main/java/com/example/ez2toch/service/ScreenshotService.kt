package com.example.ez2toch.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Callback interface for screenshot results
interface ScreenshotCallback {
    fun onScreenshotSuccess(filename: String)
    fun onScreenshotError(error: String)
    fun onScreenshotProgress(message: String)
}

// Callback interface for pixel color results
interface PixelColorCallback {
    fun onPixelColorSuccess(pixelData: PixelColorData)
    fun onPixelColorError(error: String)
    fun onPixelColorProgress(message: String)
}

// Callback interface for color analysis results
interface ColorAnalysisCallback {
    fun onColorAnalysisSuccess(colorData: ColorAnalysisData)
    fun onColorAnalysisError(error: String)
    fun onColorAnalysisProgress(message: String)
}

// Data class for pixel color results
data class PixelColorData(
    val x: Int,                       // X coordinate
    val y: Int,                       // Y coordinate
    val color: Int,                   // Color value (ARGB)
    val hex: String,                  // Hex representation (#RRGGBB)
    val rgb: Triple<Int, Int, Int>,   // RGB values
    val alpha: Int,                   // Alpha value (0-255)
    val brightness: Float,            // Brightness (0.0 - 1.0)
    val screenshotSaved: Boolean,     // Whether screenshot was saved
    val filename: String?             // Filename if saved, null if not
)

// Data class for color analysis results
data class ColorAnalysisData(
    val dominantColor: Int,           // Most common color (ARGB)
    val dominantColorHex: String,     // Hex representation
    val colorPalette: List<ColorInfo>, // Top colors with percentages
    val averageColor: Int,            // Average color of entire image
    val brightness: Float,            // Overall brightness (0.0 - 1.0)
    val contrast: Float,              // Overall contrast (0.0 - 1.0)
    val screenshotSaved: Boolean,     // Whether screenshot was saved
    val filename: String?             // Filename if saved, null if not
)

data class ColorInfo(
    val color: Int,                   // Color value (ARGB)
    val hex: String,                  // Hex representation
    val percentage: Float,            // Percentage of image (0.0 - 100.0)
    val rgb: Triple<Int, Int, Int>    // RGB values
)

class ScreenshotService private constructor() {
    
    companion object {
        private const val TAG = "ScreenshotService"
        
        @Volatile
        private var INSTANCE: ScreenshotService? = null
        
        fun getInstance(): ScreenshotService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenshotService().also { INSTANCE = it }
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Take a screenshot and get color at specific coordinates
     * @param context Application context
     * @param x X coordinate
     * @param y Y coordinate
     * @param saveScreenshot Whether to save the screenshot to Downloads folder
     * @param callback Callback for pixel color results
     */
    fun getPixelColorAt(context: Context, x: Int, y: Int, saveScreenshot: Boolean, callback: PixelColorCallback) {
        serviceScope.launch {
            try {
                callback.onPixelColorProgress("Checking root access...")
                
                // Check if root access is available
                if (!isRootAvailable()) {
                    callback.onPixelColorError("Root access not available. Please ensure device is rooted.")
                    return@launch
                }
                
                callback.onPixelColorProgress("Checking screencap command...")
                
                // Check if screencap command is available
                if (!checkScreencapAvailable()) {
                    callback.onPixelColorError("Screencap command not found. Please ensure Android screencap binary is available.")
                    return@launch
                }
                
                callback.onPixelColorProgress("Capturing screenshot...")
                
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
                    callback.onPixelColorProgress("Processing screenshot...")
                    
                    // Read the screenshot file
                    val screenshotFile = File(screenshotPath)
                    if (screenshotFile.exists()) {
                        // Set proper permissions for the file
                        try {
                            Runtime.getRuntime().exec("su -c chmod 644 $screenshotPath").waitFor()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set file permissions: ${e.message}")
                        }
                        
                        val bitmap = BitmapFactory.decodeFile(screenshotPath)
                        if (bitmap != null) {
                            callback.onPixelColorProgress("Getting pixel color at ($x, $y)...")
                            
                            // Check if coordinates are within bounds
                            if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
                                callback.onPixelColorError("Coordinates ($x, $y) are out of bounds. Screen size: ${bitmap.width}x${bitmap.height}")
                                screenshotFile.delete()
                                return@launch
                            }
                            
                            // Get pixel color
                            val pixelData = getPixelColorFromBitmap(bitmap, x, y)
                            
                            var filename: String? = null
                            if (saveScreenshot) {
                                callback.onPixelColorProgress("Saving screenshot...")
                                filename = saveScreenshotToDownloads(bitmap, context)
                            }
                            
                            // Clean up temp file
                            screenshotFile.delete()
                            
                            // Create final result
                            val finalPixelData = pixelData.copy(
                                x = x,
                                y = y,
                                screenshotSaved = saveScreenshot,
                                filename = filename
                            )
                            
                            callback.onPixelColorSuccess(finalPixelData)
                        } else {
                            callback.onPixelColorError("Failed to decode screenshot. File may be corrupted.")
                        }
                    } else {
                        callback.onPixelColorError("Screenshot file not found at: $screenshotPath")
                    }
                } else {
                    callback.onPixelColorError("Root access denied or screencap failed. Error: $errorOutput")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Pixel color detection failed: ${e.message}", e)
                callback.onPixelColorError("Pixel color detection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Get pixel color from bitmap at specific coordinates
     */
    private fun getPixelColorFromBitmap(bitmap: Bitmap, x: Int, y: Int): PixelColorData {
        val pixel = bitmap.getPixel(x, y)
        val alpha = android.graphics.Color.alpha(pixel)
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        
        val hex = String.format("#%08X", pixel)
        val rgb = Triple(red, green, blue)
        
        // Calculate brightness (luminance)
        val brightness = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f
        
        return PixelColorData(
            x = x,
            y = y,
            color = pixel,
            hex = hex,
            rgb = rgb,
            alpha = alpha,
            brightness = brightness,
            screenshotSaved = false, // Will be set by caller
            filename = null
        )
    }
    
    /**
     * Take a screenshot and analyze colors
     * @param context Application context
     * @param saveScreenshot Whether to save the screenshot to Downloads folder
     * @param callback Callback for color analysis results
     */
    fun takeScreenshotAndAnalyzeColors(context: Context, saveScreenshot: Boolean, callback: ColorAnalysisCallback) {
        serviceScope.launch {
            try {
                callback.onColorAnalysisProgress("Checking root access...")
                
                // Check if root access is available
                if (!isRootAvailable()) {
                    callback.onColorAnalysisError("Root access not available. Please ensure device is rooted.")
                    return@launch
                }
                
                callback.onColorAnalysisProgress("Checking screencap command...")
                
                // Check if screencap command is available
                if (!checkScreencapAvailable()) {
                    callback.onColorAnalysisError("Screencap command not found. Please ensure Android screencap binary is available.")
                    return@launch
                }
                
                callback.onColorAnalysisProgress("Capturing screenshot...")
                
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
                    callback.onColorAnalysisProgress("Processing screenshot...")
                    
                    // Read the screenshot file
                    val screenshotFile = File(screenshotPath)
                    if (screenshotFile.exists()) {
                        // Set proper permissions for the file
                        try {
                            Runtime.getRuntime().exec("su -c chmod 644 $screenshotPath").waitFor()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set file permissions: ${e.message}")
                        }
                        
                        val bitmap = BitmapFactory.decodeFile(screenshotPath)
                        if (bitmap != null) {
                            callback.onColorAnalysisProgress("Analyzing colors...")
                            
                            // Analyze colors
                            val colorData = analyzeBitmapColors(bitmap)
                            
                            var filename: String? = null
                            if (saveScreenshot) {
                                callback.onColorAnalysisProgress("Saving screenshot...")
                                filename = saveScreenshotToDownloads(bitmap, context)
                            }
                            
                            // Clean up temp file
                            screenshotFile.delete()
                            
                            // Create final result
                            val finalColorData = colorData.copy(
                                screenshotSaved = saveScreenshot,
                                filename = filename
                            )
                            
                            callback.onColorAnalysisSuccess(finalColorData)
                        } else {
                            callback.onColorAnalysisError("Failed to decode screenshot. File may be corrupted.")
                        }
                    } else {
                        callback.onColorAnalysisError("Screenshot file not found at: $screenshotPath")
                    }
                } else {
                    callback.onColorAnalysisError("Root access denied or screencap failed. Error: $errorOutput")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Color analysis failed: ${e.message}", e)
                callback.onColorAnalysisError("Color analysis failed: ${e.message}")
            }
        }
    }
    
    /**
     * Analyze colors in a bitmap
     */
    private fun analyzeBitmapColors(bitmap: Bitmap): ColorAnalysisData {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        // Color frequency map
        val colorFrequency = mutableMapOf<Int, Int>()
        var totalRed = 0L
        var totalGreen = 0L
        var totalBlue = 0L
        var totalBrightness = 0.0
        
        // Sample pixels for performance (every 4th pixel)
        val sampleStep = 4
        var sampledPixels = 0
        
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                
                // Skip transparent pixels
                if (alpha > 0) {
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)
                    
                    // Add to frequency map
                    colorFrequency[pixel] = colorFrequency.getOrDefault(pixel, 0) + 1
                    
                    // Calculate totals for average color
                    totalRed += red
                    totalGreen += green
                    totalBlue += blue
                    
                    // Calculate brightness (luminance)
                    val brightness = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
                    totalBrightness += brightness
                    
                    sampledPixels++
                }
            }
        }
        
        // Calculate average color
        val avgRed = (totalRed / sampledPixels).toInt()
        val avgGreen = (totalGreen / sampledPixels).toInt()
        val avgBlue = (totalBlue / sampledPixels).toInt()
        val averageColor = android.graphics.Color.rgb(avgRed, avgGreen, avgBlue)
        
        // Calculate overall brightness
        val overallBrightness = (totalBrightness / sampledPixels).toFloat()
        
        // Get dominant color
        val dominantColor = colorFrequency.maxByOrNull { it.value }?.key ?: averageColor
        val dominantColorHex = String.format("#%06X", 0xFFFFFF and dominantColor)
        
        // Create color palette (top 10 colors)
        val colorPalette = colorFrequency.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (color, count) ->
                val percentage = (count.toFloat() / sampledPixels) * 100f
                val red = android.graphics.Color.red(color)
                val green = android.graphics.Color.green(color)
                val blue = android.graphics.Color.blue(color)
                val hex = String.format("#%06X", 0xFFFFFF and color)
                
                ColorInfo(
                    color = color,
                    hex = hex,
                    percentage = percentage,
                    rgb = Triple(red, green, blue)
                )
            }
        
        // Calculate contrast (simplified)
        val contrast = calculateContrast(bitmap, sampleStep)
        
        return ColorAnalysisData(
            dominantColor = dominantColor,
            dominantColorHex = dominantColorHex,
            colorPalette = colorPalette,
            averageColor = averageColor,
            brightness = overallBrightness,
            contrast = contrast,
            screenshotSaved = false, // Will be set by caller
            filename = null
        )
    }
    
    /**
     * Calculate contrast of the image
     */
    private fun calculateContrast(bitmap: Bitmap, sampleStep: Int): Float {
        val width = bitmap.width
        val height = bitmap.height
        val brightnessValues = mutableListOf<Float>()
        
        // Sample brightness values
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                val brightness = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f
                brightnessValues.add(brightness)
            }
        }
        
        if (brightnessValues.isEmpty()) return 0f
        
        // Calculate standard deviation as contrast measure
        val mean = brightnessValues.average().toFloat()
        val variance = brightnessValues.map { (it - mean) * (it - mean) }.average().toFloat()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Normalize to 0-1 range
        return (standardDeviation * 2).coerceIn(0f, 1f)
    }
    
    /**
     * Take a screenshot using root access
     * @param context Application context
     * @param callback Callback for results and progress updates
     */
    fun takeScreenshotWithRoot(context: Context, callback: ScreenshotCallback) {
        serviceScope.launch {
            try {
                callback.onScreenshotProgress("Checking root access...")
                
                // Check if root access is available
                if (!isRootAvailable()) {
                    callback.onScreenshotError("Root access not available. Please ensure device is rooted.")
                    return@launch
                }
                
                callback.onScreenshotProgress("Checking screencap command...")
                
                // Check if screencap command is available
                if (!checkScreencapAvailable()) {
                    callback.onScreenshotError("Screencap command not found. Please ensure Android screencap binary is available.")
                    return@launch
                }
                
                callback.onScreenshotProgress("Capturing screenshot...")
                
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
                    callback.onScreenshotProgress("Processing screenshot...")
                    
                    // Read the screenshot file
                    val screenshotFile = File(screenshotPath)
                    if (screenshotFile.exists()) {
                        // Set proper permissions for the file
                        try {
                            Runtime.getRuntime().exec("su -c chmod 644 $screenshotPath").waitFor()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to set file permissions: ${e.message}")
                        }
                        
                        val bitmap = BitmapFactory.decodeFile(screenshotPath)
                        if (bitmap != null) {
                            callback.onScreenshotProgress("Saving to Downloads...")
                            
                            // Save to Downloads folder
                            val filename = saveScreenshotToDownloads(bitmap, context)
                            if (filename != null) {
                                // Clean up temp file
                                screenshotFile.delete()
                                callback.onScreenshotSuccess(filename)
                            } else {
                                callback.onScreenshotError("Failed to save screenshot to Downloads folder")
                            }
                        } else {
                            callback.onScreenshotError("Failed to decode screenshot. File may be corrupted.")
                        }
                    } else {
                        callback.onScreenshotError("Screenshot file not found at: $screenshotPath")
                    }
                } else {
                    callback.onScreenshotError("Root access denied or screencap failed. Error: $errorOutput")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot failed: ${e.message}", e)
                callback.onScreenshotError("Screenshot failed: ${e.message}")
            }
        }
    }
    
    /**
     * Check if root access is available
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c echo test")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if screencap command is available
     */
    fun checkScreencapAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c which screencap")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Save bitmap to Downloads folder
     * @param bitmap The bitmap to save
     * @param context Application context
     * @return Filename if successful, null if failed
     */
    private fun saveScreenshotToDownloads(bitmap: Bitmap, context: Context): String? {
        return try {
            // Create filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "screenshot_$timestamp.png"
            
            // Get Downloads directory
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, filename)
            
            // Save bitmap to file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "Screenshot saved: $filename")
            filename
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get screenshot history (list of recent screenshots)
     */
    fun getScreenshotHistory(context: Context): List<String> {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.listFiles()
                ?.filter { it.name.startsWith("screenshot_") && it.name.endsWith(".png") }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.name }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screenshot history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Delete a screenshot file
     */
    fun deleteScreenshot(filename: String, context: Context): Boolean {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val file = File(downloadsDir, filename)
            val deleted = file.delete()
            Log.d(TAG, "Screenshot deleted: $filename, success: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete screenshot: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get screenshot file info
     */
    fun getScreenshotInfo(filename: String, context: Context): ScreenshotInfo? {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val file = File(downloadsDir, filename)
            if (file.exists()) {
                ScreenshotInfo(
                    filename = filename,
                    fileSize = file.length(),
                    createdDate = Date(file.lastModified()),
                    filePath = file.absolutePath
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screenshot info: ${e.message}", e)
            null
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        serviceScope.cancel()
    }
    
    /**
     * Data class for screenshot information
     */
    data class ScreenshotInfo(
        val filename: String,
        val fileSize: Long,
        val createdDate: Date,
        val filePath: String
    ) {
        fun getFormattedSize(): String {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1 -> String.format("%.1f MB", mb)
                kb >= 1 -> String.format("%.1f KB", kb)
                else -> "$fileSize bytes"
            }
        }
        
        fun getFormattedDate(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(createdDate)
        }
    }
}
