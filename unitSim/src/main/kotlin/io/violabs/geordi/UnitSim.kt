package io.violabs.geordi

import io.mockk.confirmVerified
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.verify
import io.violabs.geordi.debug.DebugLogging
import io.violabs.geordi.exceptions.NotFoundException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.test.assertFailsWith
import io.mockk.every as mockkEvery

/**
 * An abstract base class for unit tests, extending functionality with additional features like
 * debug logging, mock management, and structured test flow.
 *
 * @param testResourceFolder Directory path for test resources, if any.
 * @param debugLogging Instance for handling debug logging operations.
 * @param json Instance of the Json serializer for serialization and deserialization.
 * @param debugEnabled Flag to enable or disable debug logging.
 */
@ExtendWith(WarpDriveEngine::class)
abstract class UnitSim(
    protected val testResourceFolder: String = "",
    protected val debugLogging: DebugLogging = DebugLogging.default(),
    protected val json: Json = Json,
    internal val debugEnabled: Boolean = true
) {
    // Collection of mock objects used in the tests.
    private val mocks: MutableList<Any> = mutableListOf()

    // Collection of mock call tasks to be verified.
    private val mockCalls = mutableListOf<MockTask<*>>()

    // Storage for debug items.
    private val debugItems = mutableMapOf<String, Any?>()

    fun getTestFile(filename: String): File {
        val fullFilename = filename.takeIf { testResourceFolder.isEmpty() } ?: "$testResourceFolder/$filename"
        val uri = this::class.java.classLoader
            .getResource(fullFilename)
            ?.toURI() ?: throw NotFoundException.File(filename)
        return File(uri)
    }

    /**
     * Adds an object to debug logging with an optional custom key.
     *
     * @param key Custom key for the debug item. Defaults to the current size of the debug items map.
     * @return The original object.
     */
    fun <T> T.debug(key: String = debugItems.size.toString()): T = debugLogging.addDebugItem(key, this)

    /**
     * Creates a mock of the specified type.
     *
     * @return The created mock object.
     */
    inline fun <reified T : Any> mock(): T = mockkClass(type = T::class)

    inline fun <reified T : Any> spy(objectToCopy: T): T = spyk(objectToCopy)

    /**
     * Sets up a mock call and adds it to the list of mock calls for verification.
     *
     * @return A MockTask instance for further configuration of the mock call.
     */
    fun <T : Any> every(mockCall: () -> T?): MockTask<T> {
        val task = MockTask(mockCall = mockCall)
        mockCalls.add(task)
        return task
    }

    /**
     * Runs a test with the provided specification lambda.
     *
     * @param horizontalLogs Determines if logs should be formatted horizontally.
     * @param runnable A lambda that defines the test specifications within a [TestSlice] context.
     */
    fun <T> test(
        horizontalLogs: Boolean = false,
        runnable: TestSlice<T>.() -> Unit
    ) {
        val spec = TestSlice<T>(json, horizontalLogs)

        runnable(spec)

        processTestFlow(spec)
        if (debugEnabled) debugLogging.logDebugItems()
        finalizeMocks()
        cleanup()
    }

    /**
     * Processes the entire flow of a test case.
     *
     * Executes each phase of the test in a defined order: setup, expect, mock setup, whenever, then, and teardown.
     *
     * @param spec The TestSlice instance containing the definitions for each test phase.
     */
    internal fun processTestFlow(spec: TestSlice<*>) {
        spec.setupCall()       // Sets up the test environment.
        spec.expectCall()      // Defines the expected outcome.
        spec.mockSetupCall()   // Configures mocks for the test.
        spec.wheneverCall()    // Specifies the action under test.
        spec.thenCall()        // Asserts the outcomes of the test.
        spec.tearDownCall()    // Cleans up after the test.
    }

    /**
     * Finalizes mock setups and verifies that all specified mock calls were executed.
     *
     * Uses the mockk library's verification functions to ensure all mocks were called as expected.
     */
    internal fun finalizeMocks() {
        // Iterate through all mock calls to verify each one.
        mockCalls.forEach {
            verify { it.mockCall() }
        }

        // If no mocks were used, skip the verification process.
        if (mocks.isEmpty()) return

        // Confirm that all mocks have been verified.
        @Suppress("SpreadOperator")
        confirmVerified(*mocks.toTypedArray())
    }

    /**
     * Clears the mock calls and debug items after a test has completed.
     *
     * This ensures a clean state for subsequent tests.
     */
    internal fun cleanup() {
        mockCalls.clear()    // Clears the list of mock calls.
        debugItems.clear()   // Clears the map of debug items.
    }

    /**
     * Inner class representing a slice or phase of a unit test.
     *
     * This class encapsulates the different phases of a test case, allowing for a structured and readable test setup.
     *
     * @param T The type of the expected and actual results in the test case.
     * @param useHorizontalLogs Flag to indicate if logs should be formatted horizontally.
     */
    @Suppress("TooManyFunctions")
    open inner class TestSlice<T>(
        val json: Json?,
        private val useHorizontalLogs: Boolean = false
    ) {

        private var expected: T? = null  // The expected result of the test.
        private var actual: T? = null    // The actual result obtained from the test.

        // Function placeholders for different test phases.
        internal var setupCall: () -> Unit = {}                         // Setup phase.
        internal var expectCall: () -> Unit = {}                        // Expectation definition phase.
        internal var mockSetupCall: () -> Unit? = ::processMocks    // Mock setup phase.
        var wheneverCall: () -> Unit = {}                               // Action under test.
        internal var thenCall: () -> Unit = ::defaultThenEquals     // Assertion phase.
        internal var tearDownCall: () -> Unit = {}                      // Teardown phase.

        val objectProvider: DynamicProperties<T> = DynamicProperties()  // Provider for dynamic properties.

        /**
         * Sets up a test environment using the provided setup function.
         *
         * @param setupFn A lambda function that operates on a map of dynamic properties.
         */
        fun setup(setupFn: MutableMap<String, Any?>.() -> Unit) {
            // Assigns a lambda that executes 'setupFn' with the properties from 'objectProvider'.
            this.setupCall = { setupFn(objectProvider.assign()) }
        }

        /**
         * Similar to 'setup', it prepares the test environment.
         *
         * 'given' is an alternative naming for 'setup', providing better readability in certain contexts.
         *
         * @param setupFn A lambda function that operates on a map of dynamic properties.
         */
        fun given(setupFn: MutableMap<String, Any?>.() -> Unit) {
            // Assigns a lambda that executes 'setupFn' with the properties from 'objectProvider'.
            this.setupCall = { setupFn(objectProvider.assign()) }
        }

        /**
         * Sets the expected result for a test.
         *
         * @param givenFn A lambda that takes 'DynamicProperties<T>' and returns the expected result of type
         * T (or null).
         */
        fun expect(givenFn: (props: DynamicProperties<T>) -> T?) {
            // Assigns a lambda that sets 'expected' based on the execution of 'givenFn'.
            expectCall = {
                expected = givenFn(objectProvider)
                objectProvider.expected = expected
            }
        }

        /**
         * Sets the expected result for a test, using the content of a file.
         *
         * @param filename The name of the file whose content is to be used.
         * @param givenFn A lambda that takes a 'ProviderPair<String>' representing the file content and returns
         * the expected result.
         */
        fun expectFromFileContent(filename: String, givenFn: (fileContentProvider: ProviderPair<String>) -> T?) {
            val content = getTestFile(filename).readText()

            // Sets up the 'expect' function using the file content.
            this.expect { givenFn(ProviderPair(content)) }
        }

        /**
         * Sets up the expectation for a null result.
         */
        fun expectNull() = expect { null }

        /**
         * Extension function to set the expected result as the receiver object.
         */
        fun T.expect() = expect { this }

        /**
         * Sets up mocks for the test.
         *
         * @param mockSetupFn A lambda function for setting up mocks using dynamic properties.
         */
        fun setupMocks(mockSetupFn: (props: DynamicProperties<T>) -> Unit) {
            // Assigns a lambda that executes 'mockSetupFn' and then processes the mocks.
            mockSetupCall = {
                mockSetupFn(objectProvider)
                processMocks()
            }
        }

        /**
         * Defines a 'whenever' function to set up the test action.
         *
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and returns a value of type T (or null).
         */
        fun whenever(whenFn: (props: DynamicProperties<T>) -> T?) {
            // Assigns a lambda that executes 'whenFn' with 'objectProvider' and stores the result in 'actual'.
            wheneverCall = { actual = whenFn(objectProvider) }
        }

        /**
         * Sets up a 'whenever' function that includes a file in its execution.
         *
         * @param filename The name of the file to be used in the test.
         * @param whenFn A lambda that takes a 'ProviderPair<File>' and returns a value of type T (or null).
         */
        fun wheneverWithFile(filename: String, whenFn: (fileProvider: ProviderPair<File>) -> T?) {
            val file = getTestFile(filename)

            // Sets up the 'whenever' function using the file.
            this.whenever { whenFn(ProviderPair(file)) }
        }

        /**
         * Sets up a 'whenever' function that expects a specific exception to be thrown.
         *
         * @param U The type of the expected throwable.
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and is expected to throw an exception of type U.
         */
        inline fun <reified U : Throwable> wheneverThrows(crossinline whenFn: (props: DynamicProperties<T>) -> T) {
            // Assigns a lambda that asserts an exception of type U is thrown during the execution of 'whenFn'.
            wheneverCall = { assertFailsWith<U> { whenFn(objectProvider) } }
        }

        /**
         * Sets up a 'whenever' function that expects an exception to be thrown and allows for additional processing.
         *
         * @param U The type of the expected throwable.
         * @param whenFn A lambda that takes 'DynamicProperties<T>' and is expected to throw an exception of type U.
         * @param and A lambda for additional processing with the thrown exception.
         */
        inline fun <reified U : Throwable> wheneverThrows(
            crossinline whenFn: (props: DynamicProperties<T>) -> T,
            crossinline and: (itemProvider: ProviderPair<U>) -> Unit
        ) {
            // Assigns a lambda that asserts an exception of type U is thrown and then allows
            // for further processing with 'and'.
            wheneverCall = {
                val item: U = assertFailsWith<U> { whenFn(objectProvider) }
                and(ProviderPair(item))
            }
        }

        fun <R> assertOrLog(assertion: Boolean, expected: R?, actual: R?, message: String? = null) {
            assert(assertion) {
                debugLogging.logAssertion(expected, actual, message, useHorizontalLogs = useHorizontalLogs)
            }
        }

        /**
         * Defines a custom 'then' function to handle assertions.
         *
         * @param thenFn A lambda that takes two parameters of type T (or null) and performs an
         *               action, typically an assertion.
         */
        fun then(thenFn: (expected: T?, actual: T?) -> Unit) {
            // Assigns a lambda that executes 'thenFn' with 'expected' and 'actual' as arguments.
            thenCall = {
                thenFn(expected, actual)
            }
        }

        /**
         * Sets up an equality check with a custom message and an optional pre-assertion action.
         *
         * @param message The message to be displayed if the assertion fails.
         * @param runnable An optional lambda that takes [DynamicProperties] and performs an
         *                 action before the assertion.
         */
        fun thenEquals(message: String, runnable: ((props: DynamicProperties<T>) -> Unit)? = null) {
            // Assigns a lambda that optionally executes 'runnable' and then asserts equality
            // between 'expected' and 'actual'.
            thenCall = {
                runnable?.invoke(objectProvider)

                assertOrLog(expected == actual, expected, actual, message)
            }
        }

        /**
         * Sets up an equality check with a custom message and an optional pre-assertion action.
         *
         * @param message The message to be displayed if the assertion fails.
         * @param mappingFn A lambda that takes a value of type T and returns a value of type R.
         */
        fun <R> mapEquals(message: String? = "", mappingFn: (T?) -> R?) {
            thenCall = {
                val mappedActual = mappingFn(actual)
                val mappedExpected = mappingFn(expected)

                assertOrLog<R>(mappedExpected == mappedActual, mappedExpected, mappedActual, message)
            }
        }

        /**
         * Sets up a default equality check between 'expected' and 'actual'.
         *
         * This function asserts that 'expected' and 'actual' are equal, logging the details of the assertion.
         */
        fun defaultThenEquals() {
            // Asserts equality between 'expected' and 'actual', with logging on failure.
            assertOrLog(expected == actual, expected, actual)
        }

        /**
         * Defines a teardown function to be executed after test completion.
         *
         * @param tearDownFn A lambda that operates on a map of dynamic properties.
         */
        fun teardown(tearDownFn: MutableMap<String, Any?>.() -> Unit) {
            // Assigns a lambda that executes 'tearDownFn' with the properties assigned in 'objectProvider'.
            this.tearDownCall = { tearDownFn(objectProvider.assign()) }
        }


        /**
         * Processes mock calls and logs the results.
         *
         * This function partitions mock calls into throwables and runnables, then logs different counts
         * (thrown, called, null returned, and value returned) for each category.
         */
        private fun processMocks() = debugLogging.takeIf { mockCalls.isNotEmpty() }?.logDebugMocks {
            // Partition mock calls into those with throwables and those without (runnables).
            val (throwables, runnables) = mockCalls.partition { it.throwable != null }

            // Process and log throwable mock calls.
            throwables.onEach { mockkEvery { it.mockCall.invoke() } throws it.throwable!! }.logThrownCount()

            // Process and log runnable mock calls.
            val (callOnly, returnable) = runnables.partition { it.returnedItem == null }
            runnables.logCalledCount()
            callOnly.onEach { mockkEvery { it.mockCall.invoke() } returns Unit }.logNullCount()
            returnable.onEach { mockkEvery { it.mockCall.invoke() } returns it.returnedItem!! }.logReturnedCount()
        }

        /**
         * Inner class to manage dynamic properties.
         *
         * Allows for dynamic assignment and retrieval of properties using operators.
         *
         * @param T The type of the expected value.
         */
        inner class DynamicProperties<T> {
            private val properties = mutableMapOf<String, Any?>()

            var expected: T? = null

            // Gets a property value by its name.
            operator fun getValue(thisRef: Any?, property: KProperty<*>): Any? = properties[property.name]

            // Sets a property value by its name.
            operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Any?) {
                properties[property.name] = value
            }

            // Returns the properties map.
            internal fun assign(): MutableMap<String, Any?> = properties

            // Gets a property value by its key.
            operator fun get(key: String): Any? = properties[key]
        }

        /**
         * Inner class to pair a provided item with dynamic properties.
         *
         * @param T The type of the item.
         * @param item The item to be paired.
         */
        inner class ProviderPair<T>(val item: T) {
            // Returns the item.
            operator fun component1(): T = item

            // Returns the dynamic properties associated with the item.
            operator fun component2(): DynamicProperties<*> = objectProvider
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
        inline fun <reified T> setup(provider: T.() -> Array<Pair<SimulationGroup, KFunction<*>>>) {
            // Create a new instance of the specified class.
            val refInstance: T = T::class.java.getDeclaredConstructor().newInstance()

            // Apply the provider function to the instance and iterate over each pair.
            provider(refInstance).forEach { (scenarios, ref) ->
                // Retrieve the name of the method from the method reference.
                val methodName = ref.name

                // Associate the scenarios with the method name in the scenario store.
                WarpDriveEngine.SCENARIO_STORE[methodName] = scenarios
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
        inline fun <reified T> setup(vararg methodPairs: Pair<SimulationGroup, T.() -> KFunction<*>>) {
            // Create a new instance of the specified class.
            val refInstance: T = T::class.java.getDeclaredConstructor().newInstance()

            // Iterate over each pair in the methodPairs.
            methodPairs.forEach { (scenarios, nameFetcher) ->
                // Apply the lambda to the instance to retrieve the method name.
                val methodName = nameFetcher(refInstance).name

                // Associate the scenarios with the method name in the scenario store.
                WarpDriveEngine.SCENARIO_STORE[methodName] = scenarios
            }
        }
    }

}
