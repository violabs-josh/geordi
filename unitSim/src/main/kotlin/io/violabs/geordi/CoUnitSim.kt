package io.violabs.geordi

import io.violabs.geordi.debug.DebugLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.reflect.KFunction
import kotlin.test.assertFailsWith

/**
 * An abstract base class for coroutine unit tests, extending functionality with additional features like
 * debug logging, mock management, and structured test flow.
 *
 * @param testResourceFolder Directory path for test resources, if any.
 * @param debugLogging Instance for handling debug logging operations.
 * @param debugEnabled Flag to enable or disable debug logging.
 */
abstract class CoUnitSim(
    testResourceFolder: String = "",
    json: Json = Json,
    debugLogging: DebugLogging = DebugLogging.default(),
    debugEnabled: Boolean = true
) : UnitSim(testResourceFolder, debugLogging, json, debugEnabled) {

    /**
     * Runs a test with the provided specification lambda.
     *
     * @param horizontalLogs Determines if logs should be formatted horizontally.
     * @param runnable A lambda that defines the test specifications within a [CoTestSlice] context.
     */
    fun <T> testBlocking(
        horizontalLogs: Boolean = false,
        runnable: CoTestSlice<T>.() -> Unit
    ) = runBlocking {
        val spec = CoTestSlice<T>(horizontalLogs, json)

        runnable(spec)

        processTestFlow(spec)
        if (debugEnabled) debugLogging.logDebugItems()
        finalizeMocks()
        cleanup()
    }

    /**
     * Inner class representing a slice or phase of a unit test for coroutines.
     *
     * This class encapsulates the different phases of a test case, allowing for a structured and readable test setup.
     *
     * @param T The type of the expected and actual results in the test case.
     * @param useHorizontalLogs Flag to indicate if logs should be formatted horizontally.
     */
    @Suppress("TooManyFunctions")
    inner class CoTestSlice<T>(
        useHorizontalLogs: Boolean = false,
        json: Json? = null
    ) : UnitSim.TestSlice<T>(json, useHorizontalLogs) {
        /**
         * Sets up a test environment using the provided setup function for coroutines.
         *
         * @param setupFn A lambda function that operates on a map of dynamic properties.
         */
        fun coSetup(setupFn: suspend MutableMap<String, Any?>.() -> Unit) {
            setup {
                runBlocking {
                    setupFn()
                }
            }
        }

        /**
         * Similar to 'coSetup', it prepares the test environment.
         *
         * 'coGiven' is an alternative naming for 'coSetup', providing better readability in certain contexts.
         *
         * @param setupFn A lambda function that operates on a map of dynamic properties.
         */
        fun coGiven(setupFn: suspend MutableMap<String, Any?>.() -> Unit) {
            given {
                runBlocking {
                    setupFn()
                }
            }
        }

        /**
         * Sets the expected result for a test for coroutines.
         *
         * @param expectFn A lambda that takes 'DynamicProperties<T>' and returns the expected result of type
         * T (or null).
         */
        fun coExpect(expectFn: suspend (props: DynamicProperties<T>) -> T?) {
            expect {
                runBlocking {
                    expectFn(objectProvider)
                }
            }
        }

        /**
         * Sets the expected result for a test, using the content of a file for coroutines.
         *
         * @param filename The name of the file whose content is to be used.
         * @param givenFn A lambda that takes a 'ProviderPair<String>' representing the file content and returns
         * the expected result.
         */
        fun coExpectFromFileContent(
            filename: String,
            givenFn: suspend (fileContentProvider: ProviderPair<String>) -> T?
        ) {
            val content = getTestFile(filename).readText()

            // Sets up the 'expect' function using the file content.
            this.coExpect { givenFn(ProviderPair(content)) }
        }

        fun T.coExpect() = coExpect { this }

        /**
         * Sets up mocks for the test for coroutines.
         *
         * @param mockSetupFn A lambda function for setting up mocks using dynamic properties.
         */
        fun coSetupMocks(mockSetupFn: suspend (props: DynamicProperties<T>) -> Unit) {
            // Assigns a lambda that executes 'mockSetupFn' and then processes the mocks.
            setupMocks {
                runBlocking {
                    mockSetupFn(objectProvider)
                }
            }
        }

        /**
         * Defines a 'whenever' function to set up the test action for coroutines.
         *
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and returns a value of type T (or null).
         */
        fun coWhenever(whenFn: suspend (props: DynamicProperties<T>) -> T?) {
            whenever {
                runBlocking {
                    whenFn(objectProvider)
                }
            }
        }

        /**
         * Sets up a 'whenever' function that includes a file in its execution for coroutines.
         *
         * @param filename The name of the file to be used in the test.
         * @param whenFn A lambda that takes a 'ProviderPair<File>' and returns a value of type T (or null).
         */
        fun coWheneverWithFile(filename: String, whenFn: suspend (fileProvider: ProviderPair<File>) -> T?) {
            val file = getTestFile(filename)

            // Sets up the 'whenever' function using the file.
            this.coWhenever { whenFn(ProviderPair(file)) }
        }

        /**
         * Sets up a 'whenever' function that expects a specific exception to be thrown for coroutines.
         *
         * @param U The type of the expected throwable.
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and is expected to throw an exception of type U.
         */
        inline fun <reified U : Throwable> coWheneverThrows(
            crossinline whenFn: suspend (props: DynamicProperties<T>) -> T
        ) {
            // Assigns a lambda that asserts an exception of type U is thrown during the execution of 'whenFn'.
            wheneverCall = {
                assertFailsWith<U> {
                    runBlocking {
                        whenFn(objectProvider)
                    }
                }
            }
        }

        /**
         * Sets up a 'whenever' function that expects an exception to be thrown and allows for additional processing
         * for coroutines.
         *
         * @param U The type of the expected throwable.
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and is expected to throw an exception of type U.
         * @param and A lambda for additional processing with the thrown exception.
         */
        inline fun <reified U : Throwable> coWheneverThrows(
            crossinline whenFn: suspend (props: DynamicProperties<T>) -> T,
            crossinline and: suspend (itemProvider: ProviderPair<U>) -> Unit
        ) {
            // Assigns a lambda that asserts an exception of type U is thrown and then allows
            // for further processing with 'and'.
            wheneverCall = {
                val item: U = assertFailsWith<U> {
                    runBlocking {
                        whenFn(objectProvider)
                    }
                }
                runBlocking { and(ProviderPair(item)) }
            }
        }

        /**
         * Defines a custom 'then' function to handle assertions for coroutines.
         *
         * @param thenFn A lambda that takes two parameters of type T (or null) and performs an
         *               action, typically an assertion.
         */
        fun coThen(thenFn: suspend (expected: T?, actual: T?) -> Unit) {
            // Assigns a lambda that executes 'thenFn' with 'expected' and 'actual' as arguments.
            then { expected, actual ->
                runBlocking {
                    thenFn(expected, actual)
                }
            }
        }

        /**
         * Sets up an equality check with a custom message and an optional pre-assertion action coroutines.
         *
         * @param message The message to be displayed if the assertion fails.
         * @param runnable An optional lambda that takes [DynamicProperties<T>] and performs an
         *                 action before the assertion.
         */
        fun coThenEquals(message: String, runnable: (suspend (props: DynamicProperties<T>) -> Unit)? = null) {
            // Assigns a lambda that optionally executes 'runnable' and then asserts equality
            // between 'expected' and 'actual'.
            thenEquals(message) {
                runBlocking {
                    runnable?.invoke(objectProvider)
                }
            }
        }

        /**
         * Sets up an equality check with a custom message and an optional pre-assertion action for coroutines.
         *
         * @param message The message to be displayed if the assertion fails.
         * @param mappingFn A lambda that takes a value of type T and returns a value of type R.
         */
        fun <R> coMapEquals(message: String? = "", mappingFn: (T?) -> R?) {
            mapEquals(message) {
                runBlocking {
                    mappingFn(it)
                }
            }
        }

        /**
         * Defines a teardown function to be executed after test completion for coroutines.
         *
         * @param tearDownFn A lambda that operates on a map of dynamic properties.
         */
        fun coTeardown(tearDownFn: MutableMap<String, Any?>.() -> Unit) {
            // Assigns a lambda that executes 'tearDownFn' with the properties assigned in 'objectProvider'.
            teardown {
                runBlocking {
                    tearDownFn()
                }
            }
        }
    }

    /**
     * Companion object for the class containing these methods.
     * Provides utility functions for setting up test scenarios with associated methods.
     */
    companion object {

        /**
         * Sets up test scenarios by creating instances of the specified class and associating
         * them with simulation groups.
         *
         * This function uses reflection to create an instance of the specified class and
         * then applies a provider function to it. The provider function is expected to return an array
         * of pairs, each consisting of a `SimulationGroup` and a method reference. Each pair is then used to
         * populate the `WarpDriveEngine.SCENARIO_STORE`.
         *
         * @param T The class type for which the scenarios are being set up.
         * @param provider A lambda function that, when applied to an instance of T, provides an array of pairs of
         *                 `SimulationGroup` and method references.
         */
        inline fun <reified T> coSetup(
            crossinline provider: suspend T.() -> Array<Pair<SimulationGroup, KFunction<*>>>
        ) {
            // Create a new instance of the specified class.
            val refInstance: T = T::class.java.getDeclaredConstructor().newInstance()

            // Apply the provider function to the instance and iterate over each pair.
            runBlocking {
                provider(refInstance).forEach { (scenarios, ref) ->
                    // Retrieve the name of the method from the method reference.
                    val methodName = ref.name

                    // Associate the scenarios with the method name in the scenario store.
                    WarpDriveEngine.SCENARIO_STORE[methodName] = scenarios
                }
            }
        }

        /**
         * Sets up test scenarios with a variable number of pairs, each consisting of a `SimulationGroup`
         * and a function to
         * retrieve a method reference.
         *
         * This function creates an instance of the specified class and applies a lambda to it for each pair to retrieve
         * the method name. The scenarios are then associated with these method names in the
         * `WarpDriveEngine.SCENARIO_STORE`.
         *
         * @param T The class type for which the scenarios are being set up.
         * @param methodPairs Vararg parameter of pairs, each pair contains a `SimulationGroup` and a
         *                    lambda function to fetch the method reference.
         */
        inline fun <reified T> coSetup(vararg methodPairs: Pair<SimulationGroup, suspend T.() -> KFunction<*>>) {
            // Create a new instance of the specified class.
            val refInstance: T = T::class.java.getDeclaredConstructor().newInstance()

            // Iterate over each pair in the methodPairs.
            methodPairs.forEach { (scenarios, nameFetcher) ->
                // Apply the lambda to the instance to retrieve the method name.
                val methodName = runBlocking { nameFetcher(refInstance).name }

                // Associate the scenarios with the method name in the scenario store.
                WarpDriveEngine.SCENARIO_STORE[methodName] = scenarios
            }
        }
    }
}
