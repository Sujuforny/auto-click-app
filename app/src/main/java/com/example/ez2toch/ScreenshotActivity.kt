package com.example.ez2toch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ez2toch.service.ScreenshotService
import com.example.ez2toch.service.ScreenshotCallback
import com.example.ez2toch.service.ColorAnalysisCallback
import com.example.ez2toch.service.ColorAnalysisData
import com.example.ez2toch.service.PixelColorCallback
import com.example.ez2toch.service.PixelColorData
import com.example.ez2toch.service.OpenCVService
import com.example.ez2toch.service.ImageDetectionCallback
import com.example.ez2toch.service.ImageDetectionData
import com.example.ez2toch.ui.theme.Ez2tochTheme
import com.example.ez2toch.viewmodel.AutoClickerViewModel

class ScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ez2tochTheme {
                ScreenshotScreen()
            }
        }
    }
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ScreenshotActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotScreen() {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Pixel color coordinate inputs
    var xCoordinate by remember { mutableStateOf("") }
    var yCoordinate by remember { mutableStateOf("") }
    
    // OpenCV template image path input
    var templateImagePath by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📸 Screenshot Tool") },
                navigationIcon = {
                    TextButton(
                        onClick = { (context as ComponentActivity).finish() }
                    ) {
                        Text("← Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📸 Root Screenshot Tool",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Capture screenshots using root access without permission dialogs",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Root Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (ScreenshotService.getInstance().isRootAvailable()) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (ScreenshotService.getInstance().isRootAvailable()) "✅ Root Access Available" else "❌ Root Access Required",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (ScreenshotService.getInstance().isRootAvailable()) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (ScreenshotService.getInstance().isRootAvailable()) 
                            "Device is rooted and ready for screenshot capture" 
                        else 
                            "Please ensure your device is rooted to use this feature",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Screenshot Controls Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Screenshot Controls",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            successMessage = null
                            
                            ScreenshotService.getInstance().takeScreenshotWithRoot(context, object : ScreenshotCallback {
                                override fun onScreenshotSuccess(filename: String) {
                                    isLoading = false
                                    successMessage = "Screenshot saved: $filename"
                                }
                                
                                override fun onScreenshotError(error: String) {
                                    isLoading = false
                                    errorMessage = error
                                }
                                
                                override fun onScreenshotProgress(message: String) {
                                    // Progress updates can be shown here if needed
                                }
                            })
                        },
                        enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isLoading) "📸 Capturing Screenshot..." else "📸 Take Screenshot",
                            fontSize = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Color Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                ScreenshotService.getInstance().takeScreenshotAndAnalyzeColors(context, false, object : ColorAnalysisCallback {
                                    override fun onColorAnalysisSuccess(colorData: ColorAnalysisData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🎨 Color Analysis Complete!\n\n")
                                            append("Dominant Color: ${colorData.dominantColorHex}\n")
                                            append("Brightness: ${(colorData.brightness * 100).toInt()}%\n")
                                            append("Contrast: ${(colorData.contrast * 100).toInt()}%\n\n")
                                            append("Top Colors:\n")
                                            colorData.colorPalette.take(5).forEach { colorInfo ->
                                                append("• ${colorInfo.hex} (${String.format("%.1f", colorInfo.percentage)}%)\n")
                                            }
                                            append("\nScreenshot not saved (analysis only)")
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onColorAnalysisError(error: String) {
                                        isLoading = false
                                        errorMessage = "Color analysis failed: $error"
                                    }
                                    
                                    override fun onColorAnalysisProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎨 Analyze Only")
                        }
                        
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                ScreenshotService.getInstance().takeScreenshotAndAnalyzeColors(context, true, object : ColorAnalysisCallback {
                                    override fun onColorAnalysisSuccess(colorData: ColorAnalysisData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🎨 Color Analysis Complete!\n\n")
                                            append("Dominant Color: ${colorData.dominantColorHex}\n")
                                            append("Brightness: ${(colorData.brightness * 100).toInt()}%\n")
                                            append("Contrast: ${(colorData.contrast * 100).toInt()}%\n\n")
                                            append("Top Colors:\n")
                                            colorData.colorPalette.take(5).forEach { colorInfo ->
                                                append("• ${colorInfo.hex} (${String.format("%.1f", colorInfo.percentage)}%)\n")
                                            }
                                            if (colorData.filename != null) {
                                                append("\nScreenshot saved: ${colorData.filename}")
                                            }
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onColorAnalysisError(error: String) {
                                        isLoading = false
                                        errorMessage = "Color analysis failed: $error"
                                    }
                                    
                                    override fun onColorAnalysisProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📸 Analyze + Save")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Pixel Color Detection",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = xCoordinate,
                            onValueChange = { xCoordinate = it },
                            label = { Text("X Coordinate") },
                            placeholder = { Text("e.g., 100") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                        
                        OutlinedTextField(
                            value = yCoordinate,
                            onValueChange = { yCoordinate = it },
                            label = { Text("Y Coordinate") },
                            placeholder = { Text("e.g., 200") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val x = xCoordinate.toIntOrNull()
                                val y = yCoordinate.toIntOrNull()
                                
                                if (x == null || y == null) {
                                    errorMessage = "Please enter valid X and Y coordinates"
                                    return@Button
                                }
                                
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                ScreenshotService.getInstance().getPixelColorAt(context, x, y, false, object : PixelColorCallback {
                                    override fun onPixelColorSuccess(pixelData: PixelColorData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🎯 Pixel Color at ($x, $y):\n\n")
                                            append("Color: ${pixelData.hex}\n")
                                            append("RGB: (${pixelData.rgb.first}, ${pixelData.rgb.second}, ${pixelData.rgb.third})\n")
                                            append("Alpha: ${pixelData.alpha}\n")
                                            append("Brightness: ${(pixelData.brightness * 100).toInt()}%\n")
                                            append("\nScreenshot not saved (pixel check only)")
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onPixelColorError(error: String) {
                                        isLoading = false
                                        errorMessage = "Pixel color detection failed: $error"
                                    }
                                    
                                    override fun onPixelColorProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable() && 
                                    xCoordinate.isNotBlank() && yCoordinate.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎯 Check Pixel")
                        }
                        
                        Button(
                            onClick = {
                                val x = xCoordinate.toIntOrNull()
                                val y = yCoordinate.toIntOrNull()
                                
                                if (x == null || y == null) {
                                    errorMessage = "Please enter valid X and Y coordinates"
                                    return@Button
                                }
                                
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                ScreenshotService.getInstance().getPixelColorAt(context, x, y, true, object : PixelColorCallback {
                                    override fun onPixelColorSuccess(pixelData: PixelColorData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🎯 Pixel Color at ($x, $y):\n\n")
                                            append("Color: ${pixelData.hex}\n")
                                            append("RGB: (${pixelData.rgb.first}, ${pixelData.rgb.second}, ${pixelData.rgb.third})\n")
                                            append("Alpha: ${pixelData.alpha}\n")
                                            append("Brightness: ${(pixelData.brightness * 100).toInt()}%\n")
                                            if (pixelData.filename != null) {
                                                append("\nScreenshot saved: ${pixelData.filename}")
                                            }
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onPixelColorError(error: String) {
                                        isLoading = false
                                        errorMessage = "Pixel color detection failed: $error"
                                    }
                                    
                                    override fun onPixelColorProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable() && 
                                    xCoordinate.isNotBlank() && yCoordinate.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📸 Check + Save")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "OpenCV Image Detection (Professional)",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedTextField(
                        value = templateImagePath,
                        onValueChange = { templateImagePath = it },
                        label = { Text("Template Image Path") },
                        placeholder = { Text("e.g., /sdcard/Download/button.png") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (templateImagePath.isBlank()) {
                                    errorMessage = "Please enter a template image path"
                                    return@Button
                                }
                                
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                OpenCVService.getInstance().findImagePosition(context, templateImagePath, false, object : ImageDetectionCallback {
                                    override fun onImageDetectionSuccess(detectionData: ImageDetectionData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🔍 Image Detection Result:\n\n")
                                            append("Template: ${detectionData.templatePath}\n")
                                            append("Found: ${if (detectionData.found) "✅ YES" else "❌ NO"}\n")
                                            if (detectionData.found) {
                                                append("Position: (${detectionData.x}, ${detectionData.y})\n")
                                                append("Confidence: ${(detectionData.confidence * 100).toInt()}%\n")
                                                append("Template Size: ${detectionData.templateWidth}x${detectionData.templateHeight}\n")
                                            }
                                            append("\nScreenshot not saved (detection only)")
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onImageDetectionError(error: String) {
                                        isLoading = false
                                        errorMessage = "Image detection failed: $error"
                                    }
                                    
                                    override fun onImageDetectionProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable() && 
                                    templateImagePath.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔍 Find Image")
                        }
                        
                        Button(
                            onClick = {
                                if (templateImagePath.isBlank()) {
                                    errorMessage = "Please enter a template image path"
                                    return@Button
                                }
                                
                                isLoading = true
                                errorMessage = null
                                successMessage = null
                                
                                OpenCVService.getInstance().findImagePosition(context, templateImagePath, true, object : ImageDetectionCallback {
                                    override fun onImageDetectionSuccess(detectionData: ImageDetectionData) {
                                        isLoading = false
                                        val message = buildString {
                                            append("🔍 Image Detection Result:\n\n")
                                            append("Template: ${detectionData.templatePath}\n")
                                            append("Found: ${if (detectionData.found) "✅ YES" else "❌ NO"}\n")
                                            if (detectionData.found) {
                                                append("Position: (${detectionData.x}, ${detectionData.y})\n")
                                                append("Confidence: ${(detectionData.confidence * 100).toInt()}%\n")
                                                append("Template Size: ${detectionData.templateWidth}x${detectionData.templateHeight}\n")
                                            }
                                            if (detectionData.filename != null) {
                                                append("\nScreenshot saved: ${detectionData.filename}")
                                            }
                                        }
                                        successMessage = message
                                    }
                                    
                                    override fun onImageDetectionError(error: String) {
                                        isLoading = false
                                        errorMessage = "Image detection failed: $error"
                                    }
                                    
                                    override fun onImageDetectionProgress(message: String) {
                                        // Progress updates
                                    }
                                })
                            },
                            enabled = !isLoading && ScreenshotService.getInstance().isRootAvailable() && 
                                    templateImagePath.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📸 Find + Save")
                        }
                    }
                    
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Capturing screenshot... Please wait",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    successMessage?.let { success ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = success,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { successMessage = null }
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
            
            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How It Works",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val instructions = listOf(
                        "1. Uses root access to capture screen without permission dialogs",
                        "2. Saves screenshot to Downloads folder with timestamp",
                        "3. Automatically cleans up temporary files",
                        "4. Works with any app or game running on screen",
                        "5. Perfect for automation and game analysis"
                    )
                    
                    instructions.forEach { instruction ->
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            // File Info Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "File Information",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Screenshots are saved to:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "/Downloads/screenshot_YYYYMMDD_HHMMSS.png",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "File format: PNG (high quality)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Resolution: Full screen resolution",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Show error message if any
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { 
                                errorMessage = null
                            }
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

