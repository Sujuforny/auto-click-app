# Auto Clicker - Command File System

A powerful Android automation tool that allows you to create complex click sequences using simple text-based command files.

## Download

📱 **[Download Latest APK](https://github.com/Sujuforny/auto-click-app/releases/latest/download/app-debug.apk)**

Get the latest version of the Auto Clicker app and start automating your Android tasks today!

## Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Command Syntax](#command-syntax)
- [Basic Commands](#basic-commands)
- [Conditional Commands](#conditional-commands)
- [Variable Commands](#variable-commands)
- [Flow Control Commands](#flow-control-commands)
- [Function Commands](#function-commands)
- [Image Detection Commands](#image-detection-commands)
- [Debug and Logging Commands](#debug-and-logging-commands)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Overview

The Auto Clicker uses a text-based command file system that allows you to:
- Perform clicks, swipes, and long presses
- Use conditional logic (if, while, repeat)
- Manage variables and counters
- Control program flow with labels and goto
- Detect images on screen using OpenCV
- Display messages and debug information

## Getting Started

### 1. Enable Accessibility Service
- Go to Settings > Accessibility > Auto Clicker
- Enable the service
- Grant necessary permissions

### 2. Create Command Files
You can create command files in two ways:
- **Quick Editor**: Use the textarea in the app
- **File System**: Place `.txt` files in the app's internal storage

### 3. Execute Commands
- Use "Run Sample" to test with the included example
- Use "Execute Commands" to run textarea content
- Use "Load File" to execute custom command files

## Command Syntax

### General Rules
- One command per line
- Commands are case-insensitive
- Comments start with `#`
- Variables are referenced with `$variableName`
- Whitespace is ignored

### File Structure
```
# This is a comment
command1 parameter1 parameter2
command2 parameter1

# Another comment
command3 parameter1 parameter2 parameter3
```

## Basic Commands

### Click Commands
```bash
# Basic click at coordinates
click 500 800

# Three-finger tap
threefingertap 300 400

# Long press (duration in milliseconds)
longpress 400 500 2000


# Swipe from one point to another
swipe 100 200 500 600 1000

# Zoom in (spread gesture - x, y, distance, duration)
zoomin 500 800 400 1000

# Zoom out (pinch gesture - x, y, distance, duration)
zoomout 500 800 400 1000
```

### Timing Commands
```bash
# Delay execution (milliseconds)
delay 1000

# Stop the sequence
stop
```

### Comments
```bash
# This is a comment
# Comments are ignored during execution
```

## Conditional Commands

### If Statements
```bash
# Simple if statement
if $counter == 1
    click 300 600
    delay 500
endif

# If-else statement
if $counter >= 5
    click 500 800
    delay 1000
else
    click 200 400
    delay 500
endif
```

### While Loops
```bash
# While loop
while $counter < 10
    click 400 500
    delay 1000
    set counter $counter + 1
endwhile
```

### Repeat Loops
```bash
# Repeat a block of commands
repeat 5
    click 300 400
    delay 500
endrepeat
```

## Variable Commands

### Setting Variables
```bash
# Set a variable
set counter 0
set max_attempts 5
set status retry

# Set variable with calculation
set counter $counter + 1
set total $counter * 2
```

### Getting Variables
```bash
# Get variable value (usually used in conditions)
get counter
```

### Variable Usage in Conditions
```bash
# Compare variables
if $counter == $max_attempts
    goto end_sequence
endif

# Compare with numbers
if $counter >= 5
    logs Maximum attempts reached
endif
```

## Flow Control Commands

### Labels
```bash
# Create a label
label retry_loop
label end_sequence
label start_here
```

### Goto Commands
```bash
# Jump to a label
goto retry_loop
goto end_sequence

# Conditional jump
gotoif $counter >= 5 end_sequence
gotoif $status == success final_click
```

## Function Commands

### Function Definition
```bash
# Define a function
fun clickSequence
    logs Performing click sequence
    click 500 800
    delay 500
    click 300 600
    delay 500
    logs Click sequence completed
endfun

# Define a function for waiting
fun waitAndLog
    logs Waiting...
    delay 2000
    logs Wait completed
endfun
```

### Function Calls
```bash
# Call a function
call clickSequence
call waitAndLog

# Call functions in conditional blocks
if $counter == 1
    call clickSequence
    delay 1000
endif
```

### Function Features
- **Code Reuse**: Define common sequences once and call them multiple times
- **Organization**: Break complex automation into manageable functions
- **Nested Calls**: Functions can call other functions
- **Variable Access**: Functions can access and modify global variables

## Image Detection Commands

### Find Image Position
```bash
# Basic image detection
findImagePosition /sdcard/Download/btn.png buttonX buttonY

# With confidence score
findImagePosition /sdcard/Download/btn.png buttonX buttonY buttonConfidence
```

### Image Detection Features
- **OpenCV Integration**: Uses professional computer vision for accurate image detection
- **Multiple File Formats**: Supports PNG, JPG, and other common image formats
- **Flexible Paths**: Works with various file path formats
- **Confidence Scoring**: Optional confidence score for detection quality
- **Variable Storage**: Stores results in variables for conditional logic

### Image Detection Usage
```bash
# Find button and click if found
findImagePosition /sdcard/Download/btn.png targetX targetY
if $targetX != -1
    logs Button found! Clicking at position
    click $targetX $targetY
    delay 500
else
    logs Button not found, using fallback
    click 500 800
endif

# Use confidence score for better detection
findImagePosition /sdcard/Download/icon.png iconX iconY confidence
if $confidence > 0.8
    logs High confidence detection
    click $iconX $iconY
else
    logs Low confidence, skipping
endif
```

### Image Detection Variables
- **X Coordinate**: Position X coordinate of detected image center
- **Y Coordinate**: Position Y coordinate of detected image center  
- **Confidence**: Detection confidence score (0.0 to 1.0)
- **Not Found**: Variables set to -1 if image not detected

### Supported File Paths
```bash
# Different path formats
findImagePosition /sdcard/Download/btn.png x y
findImagePosition /storage/emulated/0/Download/btn.png x y
findImagePosition btn.png x y  # Uses filename only
```

### Image Detection Best Practices
- **Template Quality**: Use clear, high-contrast template images
- **Size Considerations**: Template should be large enough for reliable detection
- **Screen Resolution**: Test on target device resolution
- **Confidence Thresholds**: Use confidence scores for reliable detection
- **Fallback Logic**: Always provide fallback actions when image not found

## Debug and Logging Commands

### Log Commands
```bash
# Log to console (Logcat)
log Starting automation sequence
log Counter value: $counter

# Log variable value
logvar counter
logvar status
```

### Screen Display Commands
```bash
# Display message overlay on screen for 3 seconds
logs Automation sequence started!
logs Counter: $counter
logs Maximum attempts reached!
```

## Examples

### Simple Click Sequence
```bash
# Simple automation sequence
logs Starting simple sequence
click 500 800
delay 1000
click 300 600
delay 1000
logs Sequence completed
stop
```

### Conditional Automation
```bash
# Set initial variables
set counter 0
set max_attempts 5

# Start sequence
logs Starting conditional automation
delay 1000

# Label for retry loop
label retry_loop

# Increment counter
set counter $counter + 1
logs Attempt number $counter

# Click at different locations based on counter
if $counter == 1
    logs First attempt
    click 300 600
    delay 500
endif

if $counter == 2
    logs Second attempt
    click 400 700
    delay 500
endif

# Check if we should continue or stop
if $counter >= $max_attempts
    logs Maximum attempts reached
    goto end_sequence
endif

# Wait and retry
logs Waiting before retry
delay 2000
goto retry_loop

# End sequence label
label end_sequence
logs Automation completed
stop
```

### Loop with Variables
```bash
# Repeat with counter
set counter 0
repeat 3
    logs Click number $counter
    click 400 500
    delay 1000
    set counter $counter + 1
endrepeat

logs All clicks completed
stop
```

### Function-Based Automation
```bash
# Define reusable functions
fun loginSequence
    logs Starting login sequence
    click 500 800
    delay 1000
    click 300 600
    delay 1000
    logs Login sequence completed
endfun

fun waitAndRetry
    logs Waiting before retry
    delay 2000
    logs Retry ready
endfun

# Main automation using functions
logs Starting automation
call loginSequence
call waitAndRetry
call loginSequence
logs Automation completed
stop
```

### Image Detection Automation
```bash
# Define function for image-based clicking
fun findButtonAndClick
    logs Looking for button image...
    findImagePosition /sdcard/Download/btn.png buttonX buttonY buttonConfidence
    logvar buttonX
    logvar buttonY
    logvar buttonConfidence
    
    # Check if image was found with good confidence
    if $buttonX != -1
        if $buttonConfidence > 0.7
            logs Button found with high confidence! Clicking
            click $buttonX $buttonY
            delay 500
        else
            logs Button found but low confidence, using fallback
            click 500 800
            delay 500
        endif
    else
        logs Button not found, using fallback position
        click 500 800
        delay 500
    endif
endfun

# Main automation with image detection
logs Starting image detection automation
call findButtonAndClick
delay 1000
call findButtonAndClick
logs Image detection automation completed
stop
```

## Troubleshooting

### Common Issues

#### Commands Not Executing
- Ensure accessibility service is enabled
- Check that coordinates are within screen bounds
- Verify command syntax is correct

#### Variable Issues
- Variables must be set before use
- Use `$variableName` to reference variables
- Check variable names for typos

#### Loop Problems
- Ensure `endif`, `endwhile`, `endrepeat`, `endfun` close blocks
- Check loop conditions for infinite loops
- Use `stop` command to end sequences

#### Function Issues
- Functions must be defined before they are called
- Use `endfun` to properly close function definitions
- Check function names for typos in calls

#### Overlay Permission Issues
- Grant overlay permission in app settings
- Restart the app after granting permissions

#### Image Detection Issues
- Ensure template image file exists and is accessible
- Check file permissions for Downloads folder access
- Verify template image is clear and high contrast
- Test with different template image sizes
- Use confidence scores to validate detection quality
- Provide fallback actions when image not found

### Debug Tips

#### Use Log Commands
```bash
# Log important values
logvar counter
logvar status
log Current step: clicking at 500, 800
```

#### Use Screen Messages
```bash
# Display progress on screen
logs Starting step 1
logs Step 1 completed
logs Moving to step 2
```

#### Test with Simple Commands
```bash
# Start with basic commands
logs Test message
click 500 800
delay 1000
stop
```

### Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Unknown command" | Typo in command name | Check command spelling |
| "Requires parameter" | Missing required parameter | Add missing parameter |
| "Variable not found" | Using undefined variable | Set variable before use |
| "Label not found" | Referencing non-existent label | Create label before goto |
| "Function not found" | Calling undefined function | Define function before call |
| "Function definition requires name" | Missing function name | Add function name after 'fun' |

### Best Practices

1. **Start Simple**: Begin with basic commands before adding complexity
2. **Use Comments**: Document your automation logic
3. **Test Incrementally**: Add one feature at a time
4. **Use Logs**: Add logging to track execution flow
5. **Handle Errors**: Use conditional logic to handle edge cases
6. **Set Timeouts**: Use delays to prevent rapid execution
7. **Clean Up**: Always end sequences with `stop`
8. **Use Functions**: Break complex automation into reusable functions
9. **Organize Code**: Group related commands into functions
10. **Test Functions**: Test functions individually before using in main sequence

### Performance Tips

- Use appropriate delays between commands
- Avoid infinite loops without exit conditions
- Test on different screen sizes
- Consider device performance for complex sequences

## Advanced Usage

### Nested Conditions
```bash
if $counter >= 1
    if $counter <= 3
        logs Counter is between 1 and 3
        click 300 400
    else
        logs Counter is greater than 3
        click 500 600
    endif
endif
```

### Complex Variable Operations
```bash
set base_value 10
set multiplier 2
set result $base_value * $multiplier
logs Result: $result
```

### Multiple Labels and Jumps
```bash
label start
logs Starting sequence
gotoif $skip_first middle

label first_step
logs First step
click 100 200
delay 500

label middle
logs Middle step
click 300 400
delay 500

label end
logs Sequence completed
stop
```

---

For more examples and advanced usage, check the sample files included with the app or create your own command sequences using the Quick Command Editor.