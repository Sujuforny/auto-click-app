# Auto Clicker App

An Android application that provides automated tapping functionality using accessibility services.

## Features

- **Coordinate-based tapping**: Set exact X and Y coordinates for tapping
- **Configurable intervals**: Set the time between taps in milliseconds
- **Modern Material Design UI**: Clean and intuitive interface
- **Real-time service status**: Shows whether the accessibility service is enabled
- **Cross-app functionality**: Works on any app once enabled

## How to Use

### 1. Enable Accessibility Service
1. Install the app on your Android device
2. Open the app and tap "Open Settings" if the service is not enabled
3. In Accessibility Settings, find "Auto Clicker" and enable it
4. Grant all necessary permissions when prompted

### 2. Grant Overlay Permission
1. Tap "Grant Permission" if overlay permission is not enabled
2. In the overlay permission settings, enable "Display over other apps" for Auto Clicker
3. This allows the floating stop button to appear on top of other apps

### 3. Configure Settings
1. **Set Coordinates**: Enter the X and Y coordinates where you want to tap
   - You can use developer options to show pointer location for precise coordinates
   - Default coordinates are set to (500, 1000) as an example

2. **Set Interval**: Enter the time between taps in milliseconds
   - 1000ms = 1 second
   - 500ms = 0.5 seconds
   - Default is 1000ms

### 4. Start Auto Clicking
1. Tap "Start Clicking" to begin the automated tapping
2. **The app will automatically minimize to the home screen**
3. **A floating red "STOP" button will appear on top of other apps** (if overlay permission is granted)
4. Switch to any other app - the auto clicker will continue working
5. **Stop the automation by:**
   - Clicking the floating "STOP" button, OR
   - Reopening the app and tapping "Stop Clicking"

## Technical Details

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 14)
- **Architecture**: Uses AccessibilityService for cross-app functionality
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: Kotlin

## Permissions Required

- `SYSTEM_ALERT_WINDOW`: For overlay functionality (floating stop button)
- `FOREGROUND_SERVICE`: For background service operation
- `FOREGROUND_SERVICE_SPECIAL_USE`: For overlay service
- Accessibility Service: For performing gestures on other apps

## Safety Notes

- Use responsibly and in accordance with app terms of service
- Some apps may detect automated interactions
- Test coordinates carefully to avoid unintended actions
- The app requires manual accessibility service activation for security

## Building the App

```bash
./gradlew assembleDebug
```

The APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`

## Troubleshooting

- **Service not working**: Ensure accessibility service is enabled in Settings
- **Coordinates not accurate**: Use developer options to show pointer location
- **App crashes**: Check that all permissions are granted
- **Tapping too fast/slow**: Adjust the interval setting accordingly
