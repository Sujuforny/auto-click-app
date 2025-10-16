package com.example.ez2toch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ez2toch.ui.theme.Ez2tochTheme
import com.example.ez2toch.viewmodel.AutoClickerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ez2tochTheme {
                AutoClickerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickerApp(
    viewModel: AutoClickerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Clicker") }
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
            
            // Service Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isServiceEnabled) 
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
                        text = if (uiState.isServiceEnabled) "Service Enabled" else "Service Disabled",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (uiState.isServiceEnabled) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (uiState.isServiceEnabled) 
                            "Auto Clicker service is running" 
                        else 
                            "Please enable Auto Clicker in Accessibility Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (!uiState.isServiceEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { openAccessibilitySettings(context) }
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
            }
            
            // Overlay Permission Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.hasOverlayPermission) 
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
                        text = if (uiState.hasOverlayPermission) "Overlay Permission Granted" else "Overlay Permission Required",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (uiState.hasOverlayPermission) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (uiState.hasOverlayPermission) 
                            "Floating stop button will be available" 
                        else 
                            "Please grant overlay permission for floating stop button",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (!uiState.hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { requestOverlayPermission(context) }
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
            
            // Coordinate Input Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tap Coordinates",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.xCoordinate,
                            onValueChange = viewModel::updateXCoordinate,
                            label = { Text("X Coordinate") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        OutlinedTextField(
                            value = uiState.yCoordinate,
                            onValueChange = viewModel::updateYCoordinate,
                            label = { Text("Y Coordinate") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    
                    Text(
                        text = "Enter the screen coordinates where you want to tap",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Interval Input Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Click Interval",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    OutlinedTextField(
                        value = uiState.intervalMs,
                        onValueChange = viewModel::updateInterval,
                        label = { Text("Interval (milliseconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("ms") }
                    )
                    
                    Text(
                        text = "Time between each tap in milliseconds (1000ms = 1 second)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Control Buttons
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Controls",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.startClicking()
                                // Minimize the app to home screen
                                activity.moveTaskToBack(true)
                            },
                            enabled = uiState.isServiceEnabled && !uiState.isClicking && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (uiState.isLoading) "Starting..." else "Start Clicking")
                        }
                        
                        Button(
                            onClick = {
                                viewModel.stopClicking()
                            },
                            enabled = uiState.isClicking && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(if (uiState.isLoading) "Stopping..." else "Stop Clicking")
                        }
                    }
                    
                    if (uiState.isClicking) {
                        Text(
                            text = if (uiState.hasOverlayPermission) 
                                "Auto clicking is active - floating stop button available" 
                            else 
                                "Auto clicking is active - app minimized",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = if (uiState.hasOverlayPermission) 
                                "App will minimize, floating stop button will appear" 
                            else 
                                "App will minimize when clicking starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Show error message if any
                    uiState.errorMessage?.let { error ->
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
                                    onClick = { viewModel.clearError() }
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
            
            // Command File Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Command File",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Execute complex sequences from command files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val conditionalPath = viewModel.createConditionalSampleFile()
                                    viewModel.executeCommandFile(conditionalPath)
                                    activity.moveTaskToBack(true)
                                } catch (e: Exception) {
                                    // Error will be shown in UI state
                                }
                            },
                            enabled = uiState.isServiceEnabled && !uiState.isClicking && !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run Sample")
                        }
                        
                        Button(
                            onClick = {
                                // TODO: Add file picker for custom command files
                            },
                            enabled = uiState.isServiceEnabled && !uiState.isClicking && !uiState.isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load File")
                        }
                    }
                    
                    Text(
                        text = "Sample file location: ${viewModel.getCommandFilesDirectory()}/conditional_sample.txt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Command Text Area Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Command Editor",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "Write and execute commands directly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = uiState.commandText,
                        onValueChange = viewModel::updateCommandText,
                        label = { Text("Commands") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Enter commands here...\nExample:\nlogs Hello World\nclick 500 800\ndelay 1000") },
                        maxLines = 10
                    )
                    
                    Button(
                        onClick = {
                            viewModel.executeCommandText()
                            activity.moveTaskToBack(true)
                        },
                        enabled = uiState.isServiceEnabled && !uiState.isClicking && !uiState.isLoading && uiState.commandText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isLoading) "Executing..." else "Execute Commands")
                    }
                }
            }
            
            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "1. Enable Auto Clicker in Accessibility Settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. Grant overlay permission for floating stop button",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. Set the coordinates where you want to tap",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. Set the interval between taps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "5. Press 'Start Clicking' to begin (app will minimize)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "6. Use floating stop button or reopen app to stop",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Command File Commands:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    Text(
                        text = "Basic Commands:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• click x y - Tap at coordinates",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• delay ms - Wait for milliseconds",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• threefingertap x y - Three finger tap",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• swipe startX startY endX endY duration - Swipe gesture",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• longpress x y duration - Long press",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• stop - Stop the sequence",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Conditional Commands:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• if condition ... endif - Conditional execution",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• while condition ... endwhile - Loop while true",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• repeat count ... endrepeat - Repeat N times",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Flow Control Commands:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• label name - Create a label",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• goto name - Jump to label",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• gotoif condition name - Jump if condition true",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Variable Commands:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• set name value - Set variable",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• get name - Get variable value",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Debug Commands:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• log message - Log a message",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• logvar name - Log variable value",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• logs message - Display message overlay on screen for 3 seconds",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Condition Examples:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "• \$var == 5 - Variable equals 5",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• @counter < 10 - Counter less than 10",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• \$status != error - Status not error",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    context.startActivity(intent)
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}