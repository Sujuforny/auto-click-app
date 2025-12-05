package com.example.ez2toch.model

sealed class Command {
    data class Click(val x: Int, val y: Int) : Command()
    data class MultiClick(val points: List<Pair<Int, Int>>) : Command()
    data class Delay(val milliseconds: Long) : Command()
    data class ThreeFingerTap(val x: Int, val y: Int) : Command()
    data class Swipe(
            val startX: Int,
            val startY: Int,
            val endX: Int,
            val endY: Int,
            val duration: Long
    ) : Command()
    data class LongPress(val x: Int, val y: Int, val duration: Long) : Command()
    data class ZoomIn(val x: Int, val y: Int, val distance: Int, val duration: Long) : Command()
    data class ZoomOut(val x: Int, val y: Int, val distance: Int, val duration: Long) : Command()
    data class ContinuousSwipe(val points: List<Pair<Int, Int>>, val duration: Long) : Command()
    object Stop : Command()
    data class Comment(val text: String) : Command()

    // Conditional commands
    data class If(
            val condition: String,
            val thenCommands: List<Command>,
            val elseCommands: List<Command> = emptyList()
    ) : Command()
    data class While(val condition: String, val commands: List<Command>) : Command()
    data class Repeat(val count: Int, val commands: List<Command>) : Command()

    // Variable and state commands
    data class SetVariable(val name: String, val value: String) : Command()
    data class GetVariable(val name: String) : Command()
    data class CheckColor(val x: Int, val y: Int, val expectedColor: String) : Command()
    data class CheckText(val x: Int, val y: Int, val expectedText: String) : Command()

    // Flow control commands
    data class Label(val name: String) : Command()
    data class Goto(val labelName: String) : Command()
    data class GotoIf(val condition: String, val labelName: String) : Command()

    // Debug and logging commands
    data class Log(val message: String) : Command()
    data class LogVariable(val variableName: String) : Command()
    data class Logs(val message: String) : Command() // Display text on screen for 3 seconds

    // Function commands
    data class FunctionDef(val name: String, val commands: List<Command>) : Command()
    data class FunctionCall(val name: String) : Command()

    // Image detection command
    data class FindImagePosition(
            val templatePath: String,
            val xVariable: String,
            val yVariable: String,
            val confidenceVariable: String? = null
    ) : Command()
}

data class CommandSequence(
        val commands: List<Command>,
        val repeatCount: Int = 1,
        val repeatDelay: Long = 0
)

data class ExecutionContext(
        val variables: MutableMap<String, String> = mutableMapOf(),
        val loopCounters: MutableMap<String, Int> = mutableMapOf(),
        val labels: MutableMap<String, Int> = mutableMapOf(),
        val functions: MutableMap<String, List<Command>> = mutableMapOf()
) {
    fun setVariable(name: String, value: String) {
        variables[name] = value
    }

    fun getVariable(name: String): String? = variables[name]

    fun incrementCounter(name: String): Int {
        val current = loopCounters[name] ?: 0
        val newValue = current + 1
        loopCounters[name] = newValue
        return newValue
    }

    fun resetCounter(name: String) {
        loopCounters[name] = 0
    }

    fun getCounter(name: String): Int = loopCounters[name] ?: 0

    fun setLabel(name: String, index: Int) {
        labels[name] = index
    }

    fun getLabelIndex(name: String): Int? = labels[name]

    fun setFunction(name: String, commands: List<Command>) {
        functions[name] = commands
    }

    fun getFunction(name: String): List<Command>? = functions[name]

    fun hasFunction(name: String): Boolean = functions.containsKey(name)
}
