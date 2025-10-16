package com.example.ez2toch.parser

import com.example.ez2toch.model.Command
import com.example.ez2toch.model.ExecutionContext
import android.util.Log

object CommandParser {
    private const val TAG = "CommandParser"
    
    fun parseCommands(content: String): List<Command> {
        val lines = content.lines().mapIndexed { index, line -> 
            ParsedLine(index + 1, line.trim()) 
        }.filter { it.content.isNotEmpty() && !it.content.startsWith("#") }
        
        // First pass: collect all labels
        val labels = mutableMapOf<String, Int>()
        lines.forEachIndexed { index, line ->
            if (line.content.startsWith("label ")) {
                val labelName = line.content.substring(6).trim()
                labels[labelName] = index
            }
        }
        
        // Second pass: parse commands with label information
        val commands = parseCommandBlock(lines, 0, labels).first
        return commands
    }
    
    private data class ParsedLine(val lineNumber: Int, val content: String)
    
    private fun parseCommandBlock(lines: List<ParsedLine>, startIndex: Int, labels: Map<String, Int> = emptyMap()): Pair<List<Command>, Int> {
        val commands = mutableListOf<Command>()
        var index = startIndex
        
        while (index < lines.size) {
            val line = lines[index]
            
            try {
                when {
                    line.content.startsWith("if ") -> {
                        val (ifCommand, newIndex) = parseIfStatement(lines, index, labels)
                        commands.add(ifCommand)
                        index = newIndex
                    }
                    line.content.startsWith("while ") -> {
                        val (whileCommand, newIndex) = parseWhileStatement(lines, index, labels)
                        commands.add(whileCommand)
                        index = newIndex
                    }
                    line.content.startsWith("repeat ") -> {
                        val (repeatCommand, newIndex) = parseRepeatStatement(lines, index, labels)
                        commands.add(repeatCommand)
                        index = newIndex
                    }
                    line.content == "end" || line.content == "endif" || line.content == "endwhile" || line.content == "endrepeat" -> {
                        // End of block, return current position
                        break
                    }
                    else -> {
                        val command = parseLine(line.content, labels)
                        commands.add(command)
                        index++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing line ${line.lineNumber}: ${line.content} - ${e.message}")
                index++
            }
        }
        
        return Pair(commands, index)
    }
    
    private fun parseIfStatement(lines: List<ParsedLine>, startIndex: Int, labels: Map<String, Int> = emptyMap()): Pair<Command.If, Int> {
        val ifLine = lines[startIndex]
        val condition = ifLine.content.substring(3).trim() // Remove "if "
        
        var index = startIndex + 1
        val thenCommands = mutableListOf<Command>()
        val elseCommands = mutableListOf<Command>()
        
        // Parse then block
        val (thenBlock, newIndex) = parseCommandBlock(lines, index, labels)
        thenCommands.addAll(thenBlock)
        index = newIndex
        
        // Check for else block
        if (index < lines.size && lines[index].content.startsWith("else")) {
            index++ // Skip "else" line
            val (elseBlock, finalIndex) = parseCommandBlock(lines, index, labels)
            elseCommands.addAll(elseBlock)
            index = finalIndex
        }
        
        return Pair(Command.If(condition, thenCommands, elseCommands), index)
    }
    
    private fun parseWhileStatement(lines: List<ParsedLine>, startIndex: Int, labels: Map<String, Int> = emptyMap()): Pair<Command.While, Int> {
        val whileLine = lines[startIndex]
        val condition = whileLine.content.substring(6).trim() // Remove "while "
        
        var index = startIndex + 1
        val commands = mutableListOf<Command>()
        
        val (whileBlock, newIndex) = parseCommandBlock(lines, index, labels)
        commands.addAll(whileBlock)
        index = newIndex
        
        return Pair(Command.While(condition, commands), index)
    }
    
    private fun parseRepeatStatement(lines: List<ParsedLine>, startIndex: Int, labels: Map<String, Int> = emptyMap()): Pair<Command.Repeat, Int> {
        val repeatLine = lines[startIndex]
        val parts = repeatLine.content.split("\\s+".toRegex())
        
        if (parts.size != 2) {
            throw IllegalArgumentException("Repeat requires count")
        }
        
        val count = parts[1].toInt()
        
        var index = startIndex + 1
        val commands = mutableListOf<Command>()
        
        val (repeatBlock, newIndex) = parseCommandBlock(lines, index, labels)
        commands.addAll(repeatBlock)
        index = newIndex
        
        return Pair(Command.Repeat(count, commands), index)
    }
    
    private fun parseLine(line: String, labels: Map<String, Int> = emptyMap()): Command {
        val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        if (parts.isEmpty()) {
            throw IllegalArgumentException("Empty command")
        }
        
        val commandType = parts[0].lowercase()
        
        return when (commandType) {
            "click" -> {
                if (parts.size != 3) throw IllegalArgumentException("Click requires x and y coordinates")
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                Command.Click(x, y)
            }
            
            "delay" -> {
                if (parts.size != 2) throw IllegalArgumentException("Delay requires duration in milliseconds")
                val duration = parts[1].toLong()
                Command.Delay(duration)
            }
            
            "threefingertap", "three_finger_tap" -> {
                if (parts.size != 3) throw IllegalArgumentException("ThreeFingerTap requires x and y coordinates")
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                Command.ThreeFingerTap(x, y)
            }
            
            "swipe" -> {
                if (parts.size != 6) throw IllegalArgumentException("Swipe requires startX, startY, endX, endY, duration")
                val startX = parts[1].toInt()
                val startY = parts[2].toInt()
                val endX = parts[3].toInt()
                val endY = parts[4].toInt()
                val duration = parts[5].toLong()
                Command.Swipe(startX, startY, endX, endY, duration)
            }
            
            "longpress", "long_press" -> {
                if (parts.size != 4) throw IllegalArgumentException("LongPress requires x, y, and duration")
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                val duration = parts[3].toLong()
                Command.LongPress(x, y, duration)
            }
            
            "stop" -> {
                Command.Stop
            }
            
            "set" -> {
                if (parts.size != 3) throw IllegalArgumentException("Set requires variable name and value")
                val name = parts[1]
                val value = parts[2]
                Command.SetVariable(name, value)
            }
            
            "get" -> {
                if (parts.size != 2) throw IllegalArgumentException("Get requires variable name")
                val name = parts[1]
                Command.GetVariable(name)
            }
            
            "checkcolor" -> {
                if (parts.size != 4) throw IllegalArgumentException("CheckColor requires x, y, and expected color")
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                val expectedColor = parts[3]
                Command.CheckColor(x, y, expectedColor)
            }
            
            "checktext" -> {
                if (parts.size != 4) throw IllegalArgumentException("CheckText requires x, y, and expected text")
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                val expectedText = parts[3]
                Command.CheckText(x, y, expectedText)
            }
            
            "label" -> {
                if (parts.size != 2) throw IllegalArgumentException("Label requires name")
                val name = parts[1]
                Command.Label(name)
            }
            
            "goto" -> {
                if (parts.size != 2) throw IllegalArgumentException("Goto requires label name")
                val labelName = parts[1]
                Command.Goto(labelName)
            }
            
            "gotoif" -> {
                if (parts.size != 3) throw IllegalArgumentException("GotoIf requires condition and label name")
                val condition = parts[1]
                val labelName = parts[2]
                Command.GotoIf(condition, labelName)
            }
            
            "log" -> {
                if (parts.size < 2) throw IllegalArgumentException("Log requires a message")
                val message = parts.drop(1).joinToString(" ")
                Command.Log(message)
            }
            
            "logvar" -> {
                if (parts.size != 2) throw IllegalArgumentException("LogVar requires variable name")
                val variableName = parts[1]
                Command.LogVariable(variableName)
            }
            
            "logs" -> {
                if (parts.size < 2) throw IllegalArgumentException("Logs requires a message")
                val message = parts.drop(1).joinToString(" ")
                Command.Logs(message)
            }
            
            else -> {
                throw IllegalArgumentException("Unknown command: $commandType")
            }
        }
    }
    
    fun validateCommands(commands: List<Command>): List<String> {
        val errors = mutableListOf<String>()
        
        for ((index, command) in commands.withIndex()) {
            when (command) {
                is Command.Click -> {
                    if (command.x < 0 || command.y < 0) {
                        errors.add("Command ${index + 1}: Click coordinates must be positive")
                    }
                }
                is Command.Delay -> {
                    if (command.milliseconds < 0) {
                        errors.add("Command ${index + 1}: Delay must be positive")
                    }
                }
                is Command.ThreeFingerTap -> {
                    if (command.x < 0 || command.y < 0) {
                        errors.add("Command ${index + 1}: ThreeFingerTap coordinates must be positive")
                    }
                }
                is Command.Swipe -> {
                    if (command.startX < 0 || command.startY < 0 || 
                        command.endX < 0 || command.endY < 0 || 
                        command.duration <= 0) {
                        errors.add("Command ${index + 1}: Swipe parameters must be valid")
                    }
                }
                is Command.LongPress -> {
                    if (command.x < 0 || command.y < 0 || command.duration <= 0) {
                        errors.add("Command ${index + 1}: LongPress parameters must be valid")
                    }
                }
                else -> { /* No validation needed for other commands */ }
            }
        }
        
        return errors
    }
    
    fun evaluateCondition(condition: String, context: ExecutionContext): Boolean {
        return try {
            when {
                condition.contains("==") -> {
                    val parts = condition.split("==").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context)
                        val right = resolveVariable(parts[1], context)
                        left == right
                    } else false
                }
                condition.contains("!=") -> {
                    val parts = condition.split("!=").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context)
                        val right = resolveVariable(parts[1], context)
                        left != right
                    } else false
                }
                condition.contains(">") -> {
                    val parts = condition.split(">").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context).toIntOrNull() ?: 0
                        val right = resolveVariable(parts[1], context).toIntOrNull() ?: 0
                        left > right
                    } else false
                }
                condition.contains("<") -> {
                    val parts = condition.split("<").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context).toIntOrNull() ?: 0
                        val right = resolveVariable(parts[1], context).toIntOrNull() ?: 0
                        left < right
                    } else false
                }
                condition.contains(">=") -> {
                    val parts = condition.split(">=").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context).toIntOrNull() ?: 0
                        val right = resolveVariable(parts[1], context).toIntOrNull() ?: 0
                        left >= right
                    } else false
                }
                condition.contains("<=") -> {
                    val parts = condition.split("<=").map { it.trim() }
                    if (parts.size == 2) {
                        val left = resolveVariable(parts[0], context).toIntOrNull() ?: 0
                        val right = resolveVariable(parts[1], context).toIntOrNull() ?: 0
                        left <= right
                    } else false
                }
                else -> {
                    // Simple boolean check
                    val value = resolveVariable(condition, context)
                    value.lowercase() == "true" || value == "1"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating condition '$condition': ${e.message}")
            false
        }
    }
    
    private fun resolveVariable(value: String, context: ExecutionContext): String {
        return when {
            value.startsWith("$") -> {
                val varName = value.substring(1)
                context.getVariable(varName) ?: "0"
            }
            value.startsWith("@") -> {
                val counterName = value.substring(1)
                context.getCounter(counterName).toString()
            }
            else -> value
        }
    }
}
