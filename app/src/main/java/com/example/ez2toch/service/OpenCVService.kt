package com.example.ez2toch.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

// Callback interface for image detection results
interface ImageDetectionCallback {
    fun onImageDetectionSuccess(detectionData: ImageDetectionData)
    fun onImageDetectionError(error: String)
    fun onImageDetectionProgress(message: String)
}

// Data class for image detection results
data class ImageDetectionData(
    val templatePath: String,         // Path to template image
    val found: Boolean,               // Whether template was found
    val x: Int,                       // X coordinate of found image (center)
    val y: Int,                       // Y coordinate of found image (center)
    val confidence: Double,           // Confidence score (0.0 - 1.0)
    val templateWidth: Int,           // Width of template image
    val templateHeight: Int,           // Height of template image
    val screenshotSaved: Boolean,     // Whether screenshot was saved
    val filename: String?              // Filename if saved, null if not
)

class OpenCVService private constructor() {
    
    companion object {
        private const val TAG = "OpenCVService"
        private const val MAX_CACHE_SIZE = 10 * 1024 * 1024 // 10MB cache
        private const val CONFIDENCE_THRESHOLD = 0.7
        private const val MAX_IMAGE_SIZE = 1920 // Max width/height for processing
        
        @Volatile
        private var INSTANCE: OpenCVService? = null
        
        fun getInstance(): OpenCVService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OpenCVService().also { INSTANCE = it }
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isOpenCVInitialized = false
    
    // Performance optimizations
    private val bitmapCache = LruCache<String, Bitmap>(MAX_CACHE_SIZE / (1024 * 1024)) // Cache in MB
    private val rootCheckCache = ConcurrentHashMap<String, Boolean>()
    private val screencapCheckCache = ConcurrentHashMap<String, Boolean>()
    
    // Reusable Mat objects to avoid allocation overhead
    private val reusableMats = ThreadLocal<ReusableMatObjects>()
    
    private class ReusableMatObjects {
        val screenshotMat = Mat()
        val templateMat = Mat()
        val screenshotGray = Mat()
        val templateGray = Mat()
        val result = Mat()
        
        fun release() {
            screenshotMat.release()
            templateMat.release()
            screenshotGray.release()
            templateGray.release()
            result.release()
        }
    }
    
    /**
     * Initialize OpenCV
     */
    fun initializeOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        
        return try {
            val success = OpenCVLoader.initDebug()
            if (success) {
                isOpenCVInitialized = true
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV initialization error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Take screenshot and find template image position using OpenCV
     * @param context Application context
     * @param templatePath Path to template image file
     * @param saveScreenshot Whether to save the screenshot to Downloads folder
     * @param callback Callback for image detection results
     */
    fun findImagePosition(
        context: Context, 
        templatePath: String, 
        saveScreenshot: Boolean, 
        callback: ImageDetectionCallback
    ) {
        serviceScope.launch {
            try {
                // Parallel initialization and checks for better performance
                val initDeferred = async { initializeOpenCV() }
                val rootCheckDeferred = async { isRootAvailableCached() }
                val screencapCheckDeferred = async { checkScreencapAvailableCached() }
                val templateLoadDeferred = async { loadTemplateImageOptimized(context, templatePath) }
                
                callback.onImageDetectionProgress("Initializing...")
                
                // Wait for all parallel operations
                val initResult = initDeferred.await()
                val rootAvailable = rootCheckDeferred.await()
                val screencapAvailable = screencapCheckDeferred.await()
                val templateBitmap = templateLoadDeferred.await()
                
                if (!initResult) {
                    callback.onImageDetectionError("Failed to initialize OpenCV. Please ensure OpenCV is properly installed.")
                    return@launch
                }
                
                if (!rootAvailable) {
                    callback.onImageDetectionError("Root access not available. Please ensure device is rooted.")
                    return@launch
                }
                
                if (!screencapAvailable) {
                    callback.onImageDetectionError("Screencap command not found. Please ensure Android screencap binary is available.")
                    return@launch
                }
                
                if (templateBitmap == null) {
                    callback.onImageDetectionError("Failed to load template image. Please check the file path and permissions.")
                    return@launch
                }
                
                callback.onImageDetectionProgress("Capturing screenshot...")
                
                // Take screenshot with optimized method
                val screenshotBitmap = takeScreenshotOptimized(context)
                if (screenshotBitmap == null) {
                    callback.onImageDetectionError("Failed to capture screenshot.")
                    return@launch
                }
                
                callback.onImageDetectionProgress("Performing OpenCV template matching...")
                
                // Perform optimized OpenCV template matching
                val detectionResult = performOpenCVTemplateMatchingOptimized(screenshotBitmap, templateBitmap)
                
                var filename: String? = null
                if (saveScreenshot) {
                    callback.onImageDetectionProgress("Saving screenshot...")
                    filename = saveScreenshotToDownloads(screenshotBitmap, context)
                }
                
                // Create final result
                val finalResult = detectionResult.copy(
                    templatePath = templatePath,
                    screenshotSaved = saveScreenshot,
                    filename = filename
                )
                
                callback.onImageDetectionSuccess(finalResult)
                
            } catch (e: Exception) {
                Log.e(TAG, "Image detection failed: ${e.message}", e)
                callback.onImageDetectionError("Image detection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Optimized OpenCV template matching with reusable Mat objects
     */
    private fun performOpenCVTemplateMatchingOptimized(screenshotBitmap: Bitmap, templateBitmap: Bitmap): ImageDetectionData {
        val mats = reusableMats.get() ?: ReusableMatObjects().also { reusableMats.set(it) }
        
        try {
            // Convert bitmaps to OpenCV Mat
            Utils.bitmapToMat(screenshotBitmap, mats.screenshotMat)
            Utils.bitmapToMat(templateBitmap, mats.templateMat)
            
            // Convert to grayscale for better matching
            Imgproc.cvtColor(mats.screenshotMat, mats.screenshotGray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(mats.templateMat, mats.templateGray, Imgproc.COLOR_BGR2GRAY)
            
            // Perform template matching
            Imgproc.matchTemplate(mats.screenshotGray, mats.templateGray, mats.result, Imgproc.TM_CCOEFF_NORMED)
            
            // Find the best match
            val minMaxLoc = Core.minMaxLoc(mats.result)
            val maxVal = minMaxLoc.maxVal
            val maxLoc = minMaxLoc.maxLoc
            
            val found = maxVal >= CONFIDENCE_THRESHOLD
            val centerX = if (found) (maxLoc.x + templateBitmap.width / 2).toInt() else -1
            val centerY = if (found) (maxLoc.y + templateBitmap.height / 2).toInt() else -1
            
            return ImageDetectionData(
                templatePath = "",
                found = found,
                x = centerX,
                y = centerY,
                confidence = maxVal,
                templateWidth = templateBitmap.width,
                templateHeight = templateBitmap.height,
                screenshotSaved = false,
                filename = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV template matching error: ${e.message}", e)
            return ImageDetectionData(
                templatePath = "",
                found = false,
                x = -1,
                y = -1,
                confidence = 0.0,
                templateWidth = templateBitmap.width,
                templateHeight = templateBitmap.height,
                screenshotSaved = false,
                filename = null
            )
        }
    }
    
    /**
     * Legacy OpenCV template matching (kept for compatibility)
     */
    private fun performOpenCVTemplateMatching(screenshotBitmap: Bitmap, templateBitmap: Bitmap): ImageDetectionData {
        // Convert bitmaps to OpenCV Mat
        val screenshotMat = Mat()
        val templateMat = Mat()
        
        Utils.bitmapToMat(screenshotBitmap, screenshotMat)
        Utils.bitmapToMat(templateBitmap, templateMat)
        
        // Convert to grayscale for better matching
        val screenshotGray = Mat()
        val templateGray = Mat()
        
        Imgproc.cvtColor(screenshotMat, screenshotGray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_BGR2GRAY)
        
        // Perform template matching
        val result = Mat()
        Imgproc.matchTemplate(screenshotGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
        
        // Find the best match
        val minMaxLoc = Core.minMaxLoc(result)
        val maxVal = minMaxLoc.maxVal
        val maxLoc = minMaxLoc.maxLoc
        
        val found = maxVal >= CONFIDENCE_THRESHOLD
        val centerX = if (found) (maxLoc.x + templateBitmap.width / 2).toInt() else -1
        val centerY = if (found) (maxLoc.y + templateBitmap.height / 2).toInt() else -1
        
        // Clean up
        screenshotMat.release()
        templateMat.release()
        screenshotGray.release()
        templateGray.release()
        result.release()
        
        return ImageDetectionData(
            templatePath = "",
            found = found,
            x = centerX,
            y = centerY,
            confidence = maxVal,
            templateWidth = templateBitmap.width,
            templateHeight = templateBitmap.height,
            screenshotSaved = false,
            filename = null
        )
    }
    
    /**
     * Optimized template image loading with caching
     */
    private fun loadTemplateImageOptimized(context: Context, templatePath: String): Bitmap? {
        val fileName = File(templatePath).name
        val cacheKey = "template_$fileName"
        
        // Check cache first
        val cachedBitmap = bitmapCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            Log.d(TAG, "Loading template from cache: $fileName")
            return cachedBitmap
        }
        
        // Load from file
        val bitmap = loadTemplateImage(context, templatePath)
        if (bitmap != null) {
            // Cache the bitmap
            bitmapCache.put(cacheKey, bitmap)
            Log.d(TAG, "Cached template: $fileName")
        }
        
        return bitmap
    }
    
    /**
     * Optimized screenshot capture
     */
    private fun takeScreenshotOptimized(context: Context): Bitmap? {
        return try {
            val screenshotPath = "${context.filesDir.absolutePath}/screenshot_temp.png"
            val command = "screencap -p $screenshotPath"
            
            val process = Runtime.getRuntime().exec("su -c $command")
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "Screenshot failed: $errorOutput")
                return null
            }
            
            val screenshotFile = File(screenshotPath)
            if (!screenshotFile.exists()) {
                Log.e(TAG, "Screenshot file not found: $screenshotPath")
                return null
            }
            
            // Set proper permissions
            try {
                Runtime.getRuntime().exec("su -c chmod 644 $screenshotPath").waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set file permissions: ${e.message}")
            }
            
            // Load and optimize bitmap
            val bitmap = BitmapFactory.decodeFile(screenshotPath)
            if (bitmap != null) {
                // Resize if too large for better performance
                val optimizedBitmap = if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
                    val scale = minOf(MAX_IMAGE_SIZE.toFloat() / bitmap.width, MAX_IMAGE_SIZE.toFloat() / bitmap.height)
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } else {
                    bitmap
                }
                
                // Clean up temp file
                screenshotFile.delete()
                
                optimizedBitmap
            } else {
                Log.e(TAG, "Failed to decode screenshot")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot error: ${e.message}", e)
            null
        }
    }
    
    /**
     * Cached root availability check
     */
    private fun isRootAvailableCached(): Boolean {
        return rootCheckCache.getOrPut("root_check") {
            isRootAvailable()
        }
    }
    
    /**
     * Cached screencap availability check
     */
    private fun checkScreencapAvailableCached(): Boolean {
        return screencapCheckCache.getOrPut("screencap_check") {
            checkScreencapAvailable()
        }
    }
    
    /**
     * Load template image with proper path handling
     */
    private fun loadTemplateImage(context: Context, templatePath: String): Bitmap? {
        return try {
            // First try to find the file using MediaStore (proper Android way)
            val fileName = File(templatePath).name
            Log.d(TAG, "Looking for file: $fileName")
            
            val mediaStoreBitmap = loadFromMediaStore(context, fileName)
            if (mediaStoreBitmap != null) {
                Log.d(TAG, "Successfully loaded from MediaStore")
                return mediaStoreBitmap
            }
            
            // Try to copy from Downloads using root access as fallback
            val rootCopyBitmap = copyFromDownloadsWithRoot(context, fileName)
            if (rootCopyBitmap != null) {
                Log.d(TAG, "Successfully loaded using root copy")
                return rootCopyBitmap
            }
            
            // Fallback to direct file access
            val possiblePaths = listOf(
                templatePath, // Original path
                templatePath.replace("/sdcard/", "/storage/emulated/0/"),
                templatePath.replace("/storage/emulated/0/", "/sdcard/"),
                getDownloadsPath(context) + "/" + fileName
            )
            
            Log.d(TAG, "Attempting to load template from paths: $possiblePaths")
            
            for (path in possiblePaths) {
                val file = File(path)
                Log.d(TAG, "Checking path: $path")
                Log.d(TAG, "File exists: ${file.exists()}")
                Log.d(TAG, "File can read: ${file.canRead()}")
                Log.d(TAG, "File absolute path: ${file.absolutePath}")
                
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Loading template from: $path")
                    return try {
                        // Try direct file access first
                        BitmapFactory.decodeFile(path)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException for direct access, trying FileInputStream: ${e.message}")
                        // If direct access fails, try with FileInputStream
                        try {
                            val inputStream = FileInputStream(file)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()
                            bitmap
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed to load image from $path: ${e2.message}")
                            // Try copying to internal storage as last resort
                            copyToInternalStorageAndLoad(context, file)
                        }
                    }
                } else {
                    Log.w(TAG, "Cannot access file at $path - exists: ${file.exists()}, canRead: ${file.canRead()}")
                }
            }
            
            Log.e(TAG, "Template image not found in any of the attempted paths: $possiblePaths")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading template image: ${e.message}", e)
            null
        }
    }
    
    /**
     * Optimized MediaStore loading with bitmap options
     */
    private fun loadFromMediaStore(context: Context, fileName: String): Bitmap? {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
            
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    
                    Log.d(TAG, "Found file in MediaStore: $uri")
                    
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.use { stream ->
                        // Use optimized bitmap options for better performance
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(stream, null, options)
                        
                        // Calculate sample size for memory optimization
                        val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
                        
                        // Reset stream and decode with sample size
                        inputStream.close()
                        val newStream = context.contentResolver.openInputStream(uri)
                        newStream?.use { newStream ->
                            val decodeOptions = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                                inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                            }
                            return BitmapFactory.decodeStream(newStream, null, decodeOptions)
                        }
                    }
                }
            }
            
            Log.w(TAG, "File not found in MediaStore: $fileName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from MediaStore: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calculate sample size for bitmap optimization
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Copy file to internal storage and load it
     */
    private fun copyToInternalStorageAndLoad(context: Context, sourceFile: File): Bitmap? {
        return try {
            val internalFile = File(context.filesDir, "template_${sourceFile.name}")
            sourceFile.copyTo(internalFile, overwrite = true)
            Log.d(TAG, "Copied template to internal storage: ${internalFile.absolutePath}")
            BitmapFactory.decodeFile(internalFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy and load template: ${e.message}", e)
            null
        }
    }
    
    /**
     * Copy file from Downloads using root access
     */
    private fun copyFromDownloadsWithRoot(context: Context, fileName: String): Bitmap? {
        return try {
            if (!isRootAvailable()) {
                Log.w(TAG, "Root not available for copying file")
                return null
            }
            
            val internalFile = File(context.filesDir, "template_$fileName")
            val sourcePath = "/sdcard/Download/$fileName"
            
            Log.d(TAG, "Copying file using root: $sourcePath -> ${internalFile.absolutePath}")
            
            val process = Runtime.getRuntime().exec("su -c cp \"$sourcePath\" \"${internalFile.absolutePath}\"")
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && internalFile.exists()) {
                Log.d(TAG, "Successfully copied file using root")
                return BitmapFactory.decodeFile(internalFile.absolutePath)
            } else {
                Log.e(TAG, "Failed to copy file using root, exit code: $exitCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file with root: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get the proper Downloads directory path
     */
    private fun getDownloadsPath(context: Context): String {
        return try {
            // Try to get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                downloadsDir.absolutePath
            } else {
                // Fallback to sdcard/Download
                "/sdcard/Download"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get Downloads directory: ${e.message}")
            "/sdcard/Download"
        }
    }
    
    /**
     * Check if root access is available
     */
    private fun isRootAvailable(): Boolean {
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
    private fun checkScreencapAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c which screencap")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Save screenshot to Downloads folder
     */
    private fun saveScreenshotToDownloads(bitmap: Bitmap, context: Context): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val filename = "screenshot_$timestamp.png"
        
        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        val file = File(downloadsDir, filename)
        val outputStream = java.io.FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        
        return filename
    }
    
    /**
     * Clear caches to free memory
     */
    fun clearCaches() {
        bitmapCache.evictAll()
        rootCheckCache.clear()
        screencapCheckCache.clear()
        Log.d(TAG, "Caches cleared")
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        return "Bitmap cache: ${bitmapCache.size()}/${bitmapCache.maxSize()}, " +
               "Root check cache: ${rootCheckCache.size}, " +
               "Screencap check cache: ${screencapCheckCache.size}"
    }
}
