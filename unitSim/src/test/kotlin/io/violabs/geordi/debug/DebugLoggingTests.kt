package io.violabs.geordi.debug

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import io.mockk.spyk
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DebugLoggingTests {
    private val logger: DefaultDebugLogging.DebugLogger = mockk(relaxed = true)
    private val diffChecker: DiffChecker = mockk(relaxed = true)
    private val testLogging = DefaultDebugLogging(logger, diffChecker)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `logAssertion will log the assertion based on defaults`() {
        testLogging.logAssertion<Int>(null, null)
        every { logger.log("EXPECT: null") } just Runs
        every { logger.log("ACTUAL: null") } just Runs

        verify(atMost = 1) {
            logger.log("EXPECT: null")
            logger.log("ACTUAL: null")
        }
    }

    @Test
    fun `logAssertion will log the assertion based on provided details`() {
        val spy = spyk(testLogging)
        // Arrange
        every { spy.makeHorizontalLogs(1, 2) } returns "horizontal logs"

        // Act
        spy.logAssertion(1, 2, "Test", true)

        // Assert
        verifyOrder {
            logger.log("FAILED Test")
            logger.log("horizontal logs")
        }
    }

    @Test
    fun `logDifference calls diffChecker`() {
        testLogging.logDifferences("first", "second")

        every { diffChecker.findDifferences("first", "second") } returns mockk<DifferenceGroup>()

        verify { diffChecker.findDifferences("first", "second") }
    }

    @Test
    fun `makeHorizontalLogs will return a string with the expected and actual values`() {
        val result = testLogging.makeHorizontalLogs(1, 2)
        assert(result == "EXPECT|ACTUAL\n1     |2") {
            """
            |expected
            |EXPECT|ACTUAL
            |1     |2 
            |actual
            |$result
            """.trimMargin()
        }
    }

    @Test
    fun `makeHorizontalLogs will return a string with the expected and actual values and a title gap`() {
        val result = testLogging.makeHorizontalLogs("Ready set", "Go")
        assert(result == "EXPECT       |ACTUAL\nReady set    |Go") {
            """
            |expected
            |EXPECT    |ACTUAL
            |Ready set |Go 
            |actual
            |$result
            """.trimMargin()
        }
    }
}
