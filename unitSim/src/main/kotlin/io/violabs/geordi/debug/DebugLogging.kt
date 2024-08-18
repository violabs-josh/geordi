package io.violabs.geordi.debug

import io.violabs.geordi.common.asLines
import kotlin.math.max

/**
 * Interface defining the contract for debug logging capabilities.
 *
 * Allows for storing, adding, and logging debug items, as well as logging debug metrics.
 */
interface DebugLogging {
    val debugItems: MutableMap<String, Any?>

    /**
     * Adds a debug item to the debug items map.
     *
     * @param key A unique key for the debug item.
     * @param value The value of the debug item.
     * @return The value passed in.
     */
    fun <T> addDebugItem(key: String, value: T): T

    /**
     * Logs all the debug items stored in the debug items map.
     */
    fun logDebugItems()

    /**
     * Logs metrics by calling functions in the MetricsReader interface.
     *
     * @param callFn Lambda with MetricsReader receiver for defining which metrics to log.
     */
    fun logDebugMocks(callFn: MetricsReader.() -> Unit)

    fun <T> logAssertion(expected: T?, actual: T?, message: String? = null, useHorizontalLogs: Boolean = false)

    fun <T> makeHorizontalLogs(expected: T?, actual: T?): String

    fun <T> logDifferences(expected: T?, actual: T?)

    /**
     * Companion object to provide a default implementation of DebugLogging.
     */
    companion object {
        fun default(): DebugLogging = DefaultDebugLogging(DefaultDebugLogging.Logger())
    }

    /**
     * Interface defining methods for reading and logging metrics.
     */
    interface MetricsReader {
        fun List<Any>.logThrownCount()
        fun List<Any>.logCalledCount()
        fun List<Any>.logReturnedCount()
        fun List<Any>.logNullCount()
    }
}

internal const val DEBUG_ITEMS_TITLE = "DEBUG ITEMS"
internal const val DEBUG_MOCKS_TITLE = "MOCK METRICS"
private const val MIN_SIZE = 26

internal const val DEBUG_DEFAULT_WIDTH = MIN_SIZE

private const val PADDING = 4
private const val DEFAULT = 0

/**
 * Default implementation of the DebugLogging interface.
 */
@Suppress("TooManyFunctions")
internal class DefaultDebugLogging(
    private val logger: DebugLogger,
    private val diffChecker: DiffChecker = DiffChecker
) : DebugLogging {
    override val debugItems = mutableMapOf<String, Any?>()
    /**
     * Adds a debug item to the debug items map.
     *
     * @param key The key under which the debug item is stored. It's a unique identifier.
     * @param value The debug item to be stored. It can be of any type.
     * @return Returns the value that was added to the debug items map.
     */
    override fun <T> addDebugItem(key: String, value: T): T {
        // Stores the value in the debugItems map with the specified key.
        debugItems[key] = value

        // Returns the stored value.
        return value
    }

    /**
     * Logs all the debug items stored in the debug items map.
     *
     * If there are no debug items, it prints a message indicating that no items were found.
     * Otherwise, it formats and prints all the debug items.
     */
    override fun logDebugItems() {
        // Check if there are any debug items to log.
        if (debugItems.isEmpty()) {
            logger.log("No debug items found")
            logger.log()
            return
        }

        // Convert the debug items map to a string representation and split it into lines.
        val debugLines = debugItems.toString().asLines()

        // Determine the maximum width for the debug item lines.
        @Suppress("SpreadOperator")
        val max = stringMax(MIN_SIZE, *debugLines.toTypedArray())

        // Create border lines for the debug log.
        val (topBorder, middleBar, bottomBorder) = Border(max)

        // Format the title line for the debug items section.
        val debugTitleLine = DEBUG_ITEMS_TITLE.debugTitleLineFormat(max)
        // Build the content of the debug log with proper formatting.
        val content = buildContent(max, debugLines)

        // Print the formatted debug log.
        logger.log(
            """
            |$topBorder
            |$debugTitleLine
            |$middleBar
            |$content
            |$bottomBorder
        """.trimMargin()
        )
        logger.log()
    }

    /**
     * Logs metrics by executing a given function within the context of a MetricsReader.
     *
     * @param callFn A lambda function with MetricsReader receiver, specifying the metrics logging logic.
     */
    override fun logDebugMocks(callFn: DebugLogging.MetricsReader.() -> Unit) {
        // Create border lines for the mock metrics log.
        val (topBorder, middleBar, bottomBorder) = Border(DEBUG_DEFAULT_WIDTH)

        // Format the title line for the mock metrics section.
        val debugTitleSpaces = " ".repeat((DEBUG_DEFAULT_WIDTH - DEBUG_MOCKS_TITLE.length) / 2)
        val debugTitleLine = "║ $debugTitleSpaces$DEBUG_MOCKS_TITLE$debugTitleSpaces ║"

        // Print the top part of the mock metrics log.
        logger.log(
            """
            |$topBorder
            |$debugTitleLine
            |$middleBar
        """.trimMargin()
        )

        // Execute the provided metrics logging function within the context of DefaultMetricReader.
        callFn(DefaultMetricReader)

        // Print the bottom border of the mock metrics log.
        logger.log(bottomBorder)
        logger.log()
    }


    /**
     * Utility class for generating ASCII art style borders in debug logs.
     */
    class Border(
        val size: Int,
        val gap: String,
        val top: String    = "╔$gap╗",
        val middle: String = "╠$gap╣",
        val bottom: String = "╚$gap╝"
    ) {
        operator fun component1() = top
        operator fun component2() = middle
        operator fun component3() = bottom

        constructor(size: Int) : this(size, "═".repeat(size + 2))
        // Left as a reference
        companion object Defaults {
            const val TOP_LEFT = "╔"
            const val TOP_RIGHT = "╗"
            const val MIDDLE_LEFT = "╠"
            const val MIDDLE_RIGHT = "╣"
            const val BOTTOM_LEFT = "╚"
            const val BOTTOM_RIGHT = "╝"
        }
    }

    /**
     * Default implementation of the MetricsReader interface for logging metrics.
     */
    object DefaultMetricReader : DebugLogging.MetricsReader {
        private val logger = Logger()
        override fun List<Any>.logThrownCount() = logCount("THROWN")
        override fun List<Any>.logCalledCount() = logCount("CALLED")
        override fun List<Any>.logReturnedCount() = logCount("RETURNED")
        override fun List<Any>.logNullCount() = logCount("NULL")

        private fun List<Any>.logCount(label: String) {
            this.count().also {
                val message = "# $label: $it"
                val spaces = " ".repeat(DEBUG_DEFAULT_WIDTH - 1 - message.length)
                logger.log("║ $message $spaces ║")
            }
        }
    }

    /**
     * Determines the maximum string length, comparing a given number with the lengths of provided strings.
     *
     * @param number An integer to compare against the lengths of the strings.
     * @param strings Vararg parameter of strings to compare their lengths.
     * @return The maximum value between the given number and the longest string length.
     */
    private fun stringMax(number: Int, vararg strings: String): Int {
        // Returns the greater value between 'number' and the length of the longest string in 'strings'.
        return max(strings.maxOfOrNull(String::length) ?: DEFAULT, number)
    }

    /**
     * Builds a formatted string content for logging, within a specified width.
     *
     * @param max The maximum width for each line of the content.
     * @param debugLines A list of strings representing the lines of the content.
     * @return A formatted string where each line is within the specified width and enclosed in borders.
     */
    private fun buildContent(max: Int, debugLines: List<String>): String {
        // Joins the strings in 'debugLines' into a single string, formatting each line to fit within 'max' width.
        return debugLines.joinToString("\n") { "║ $it${" ".repeat(max - it.length)} ║" }
    }

    /**
     * Formats a title string for use in debug logging, centering it within a specified width.
     *
     * @receiver The string to be formatted as a title.
     * @param max The maximum width for the title line.
     * @return A formatted title line, centered and enclosed within borders.
     */
    private fun String.debugTitleLineFormat(max: Int): String {
        // Calculates the amount of space needed on each side of the title to center it within 'max' width.
        val debugTitleSpaces = " ".repeat((max - this.length) / 2)
        // Adjusts for even or odd 'max' widths to ensure proper centering.
        val offset = if (max % 2 == DEFAULT) " " else ""
        // Returns the formatted title line, centered and enclosed within borders.
        return "║ $debugTitleSpaces$this$debugTitleSpaces $offset║"
    }

    override fun <T> logAssertion(
        expected: T?,
        actual: T?,
        message: String?,
        useHorizontalLogs: Boolean
    ) {
        message?.let { logger.log("FAILED $message") }
        if (useHorizontalLogs) {
            logger.log(makeHorizontalLogs(expected, actual))
        } else {
            logger.log("EXPECT: $expected")
            logger.log("ACTUAL: $actual")
        }
    }

    override fun <T> logDifferences(expected: T?, actual: T?) {
        logger.log(diffChecker.findDifferences(expected.toString(), actual.toString()))
    }

    override fun <T> makeHorizontalLogs(expected: T?, actual: T?): String {
        val expectedLines = expected.toString().asLines()
        val actualLines = actual.toString().asLines()

        val zippedWithNulls = expectedLines.zipWithNulls(actualLines)

        val max = expectedLines.maxOfOrNull(String::length) ?: DEFAULT

        val expectWord = "EXPECT"
        val actualWord = "ACTUAL"

        val numberOfSpaces = max - expectWord.length + PADDING

        val titleGap = if (numberOfSpaces < 0) "" else " ".repeat(numberOfSpaces)

        val based = zippedWithNulls.alignContent(max, " ".takeIf { numberOfSpaces < 0 } ?: "")

        return """
                |$expectWord$titleGap|$actualWord
                |$based
            """.trimMargin()
    }

    private fun List<String>.zipWithNulls(other: List<String>) = this.mapIndexed { index, e ->
        val actual = other.getOrNull(index) ?: ""

        e to actual
    }

    private fun List<Pair<String, String>>.alignContent(
        max: Int, added: String = ""
    ) = this.joinToString("\n") { (e, a) ->
        val numberOfSpaces = max - e.length

        val g = " ".repeat(numberOfSpaces + PADDING)

        "$e$g$added|$a"
    }

    interface DebugLogger {
        fun log(message: Any? = null)
    }

    internal class Logger : DebugLogger {
        override fun log(message: Any?) {
            println(message)
        }
    }
}
