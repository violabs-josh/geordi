package io.violabs.geordi

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.violabs.geordi.common.with
import io.violabs.geordi.debug.DebugLogging
import io.violabs.geordi.shared.GeordiMockCompositionService
import io.violabs.geordi.shared.GeordiMockTest
import io.violabs.geordi.shared.GeordiTestClass
import kotlinx.serialization.json.Json

class CoUnitSimTests {
    val debugLogging = mockk<DebugLogging>()

    @AfterEach
    fun teardown() {
        // confirm no additional mocks are called
        confirmVerified(debugLogging)
    }

    @Test
    fun `mock will create mock of specified type`() {
        val unitSim = object : CoUnitSim() {
            fun testMock(): DebugLogging = mock()
        }

        val mock = unitSim.testMock()

        assert(mock::class.simpleName?.contains("Subclass") == true) {
            """
                EXPECT: DebugLogging${'$'}Subclass0
                ACTUAL: ${mock::class.simpleName}
            """.trimIndent()
        }
    }

    @Test
    fun `every will create mock task`() {
        val unitSim = object : CoUnitSim() {
            fun testEvery(): MockTask<String> = every { "test" }
        }

        val mockTask = unitSim.testEvery()

        assert(mockTask.mockCall.invoke() == "test")
    }

    @Nested
    inner class DebugItemAddTests {
        @Test
        fun `debug will add debug item with default key`() {
            val debugItem = "test"
            val indexKey = "0"

            val unitSim = object : CoUnitSim(debugLogging = debugLogging) {
                fun testDebug(): String = debugItem.debug()
            }

            every { debugLogging.addDebugItem(indexKey, debugItem) } returns debugItem

            unitSim.testDebug()

            verify { debugLogging.addDebugItem(indexKey, debugItem) }
            confirmVerified(debugLogging)
        }

        @Test
        fun `debug will add debug item with specified key`() {
            val debugItem = "test"
            val providedKey = "testKey"

            val unitSim = object : CoUnitSim(debugLogging = debugLogging) {
                fun testDebug(): String = debugItem.debug(providedKey)
            }

            every { debugLogging.addDebugItem(providedKey, debugItem) } returns debugItem

            unitSim.testDebug()

            verify { debugLogging.addDebugItem(providedKey, debugItem) }
            confirmVerified(debugLogging)
        }
    }

    @Nested
    inner class CoTestSliceTests {
        @Nested
        inner class WithProvidedValues : CoUnitSim() {
            @Test
            fun `coTestSlice will process default values`() {
                val slice = CoTestSlice<String>()

                assert(slice.json == null)
                assert(!slice.useHorizontalLogs)
            }

            @Test
            fun `coTestSlice will process provided values`() {
                val slice = CoTestSlice<String>(useHorizontalLogs = true, json = Json)

                assert(slice.json == Json)
                assert(slice.useHorizontalLogs)
            }
        }

        // normal flow setup && given
        @Nested
        inner class SetupTests : CoUnitSim() {
            @Test
            fun `setup will process field correctly`() = testBlocking {
                coSetup {
                    this["test"] = true
                }

                true.coExpect()

                coWhenever {
                    it["test"]
                }
            }

            @Test
            fun `given will process field correctly`() = testBlocking {
                coGiven {
                    this["test"] = true
                }

                true.coExpect()

                coWhenever {
                    DelegateTest(it).test
                }
            }

            inner class DelegateTest(dynamicProperties: TestSlice<*>.DynamicProperties<*>) {
                val test by dynamicProperties
            }
        }

        // normal flow expect && from file && null && method based
        @Nested
        inner class ExpectedTests : CoUnitSim() {
            @Test
            fun `expect will process with provider`() = testBlocking {
                coGiven { this["test"] = true }

                coExpect { it["test"] }

                coWhenever { true }
            }

            @Test
            fun `expectFromFileContent will process with a file provided full wheneverWithFile`() = testBlocking {
                coExpectFromFileContent("simpleExample/simple_example_scenario.txt") {
                    it.item.uppercase()
                }

                coWheneverWithFile("simpleExample/simple_example_expected.md") {
                    it.item.readLines().last()
                }
            }

            @Test
            fun `expectNull `() = testBlocking {
                expectNull()

                coWhenever { null }
            }
        }

        @Nested
        inner class ExpectedProvidedFolderTests : CoUnitSim("simpleExample") {
            @Test
            fun `expectFromFileContent will process with a file provided full wheneverWithFile`() = testBlocking {
                coExpectFromFileContent("simple_example_scenario.txt") {
                    val (content) = it

                    content.uppercase()
                }

                coWheneverWithFile("simple_example_expected.md") {
                    val (file) = it

                    file.readLines().last()
                }
            }

            @Test
            fun `missing throws an expected throws an exception`() {
                assertThrows<Exception> {
                    testBlocking {
                        coExpectFromFileContent("missing.txt") { it.toString() }
                    }
                }
            }
        }

        // with mocks & throws
        @Nested
        inner class MockTests : CoUnitSim() {
            private val service = mockk<GeordiMockCompositionService>()

            private val target = GeordiMockTest(service)

            @Test
            fun `mock will process with provider`() = testBlocking {
                coExpect { true }

                coSetupMocks {
                    every { service.supply() } returns "test"
                    every { service.consume("test") }
                    every { service.accept("test") } returns true
                }

                coWhenever { target.test() }
            }

            @Test
            fun `mock throws`() = testBlocking<Unit> {
                coSetupMocks {
                    every { service.supply() } throws Exception("test")
                }

                coWheneverThrows<Exception> {
                    target.test()
                }
            }
        }

        // whenever with file
        @Nested
        inner class WheneverTests : CoUnitSim() {
            @Test
            fun `wheneverWithFile throws an exception`() = testBlocking {
                coWheneverThrows<Exception>({ coWheneverWithFile("missing.txt") { } }) {
                    assert(it.item.message == "File not found: missing.txt") {
                        """
                            
                            EXPECT: File not available missing.txt
                            ACTUAL: ${it.item.message}
                        """.trimIndent()
                    }
                }
            }

            @Test
            fun `coWheneverThrows fails with an exception`() = testBlocking {
                coWheneverThrows<Exception> {
                    throw Exception("Test failure")
                }
            }
        }

        // then default && then && then equals
        @Nested
        inner class ThenTests : CoUnitSim() {
            @Test
            fun `then processes assertion`() = testBlocking {
                coExpect { "TEST ME" }

                coWhenever { "test" }

                coThen { expect: String?, actual: String? ->
                    assert(expect != null)
                    assert(actual != null)
                    assert(expect!!.contains(actual!!.uppercase()))
                }
            }

            @Test
            fun `thenEquals assertion`() = testBlocking {
                coExpect { "TEST" }

                coWhenever { "test".uppercase() }

                coThenEquals("New message") {
                    assert(true)
                }
            }

            @Test
            fun `defaultThenEquals assertion`() = testBlocking {
                coExpect { "TEST" }

                coWhenever { "test".uppercase() }

                defaultThenEquals()
            }
        }
    }

    @Nested
    inner class StaticSetupTests {

        @Test
        fun `setup with provider will add scenario to the store`() {
            val simulationGroup = SimulationGroup.vars("test")
            val geordiTestClass = GeordiTestClass()

            CoUnitSim.coSetup<GeordiTestClass> {
                arrayOf(simulationGroup with ::`here is the method`)
            }

            val actual = WarpDriveEngine.SCENARIO_STORE[geordiTestClass::`here is the method`.name]

            assert(actual == simulationGroup) {
                """
                    EXPECT: $simulationGroup
                    ACTUAL: $actual
                """.trimIndent()
            }
        }

        @Test
        fun `setup with method pairs will add scenario to the store`() {
            val simulationGroup = SimulationGroup.vars("test")
            val geordiTestClass = GeordiTestClass()

            CoUnitSim.coSetup<GeordiTestClass>(
                simulationGroup with { ::`here is a different method` }
            )

            val actual = WarpDriveEngine.SCENARIO_STORE[geordiTestClass::`here is a different method`.name]

            assert(actual == simulationGroup) {
                """
                    EXPECT: $simulationGroup
                    ACTUAL: $actual
                """.trimIndent()
            }
        }
    }
}
