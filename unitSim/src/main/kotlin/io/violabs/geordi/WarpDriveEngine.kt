package io.violabs.geordi

import io.violabs.geordi.exceptions.NotFoundException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream
import kotlin.streams.asStream

/**
 * A class representing the engine of a warp drive mechanism for JUnit 5 test template invocations.
 *
 * This class is responsible for providing test template invocation contexts based on scenarios
 * defined in a [SimulationGroup]. It supports dynamic test generation in JUnit 5.
 * This is automatically registered when using [UnitSim] using the `@ExtendWith` annotation.
 */
class WarpDriveEngine : TestTemplateInvocationContextProvider {

    /**
     * Determines whether the test template is supported in the given context.
     *
     * This implementation always returns true, indicating support for any test template.
     *
     * @param context The context in which the support is evaluated.
     * @return Boolean indicating whether the test template is supported.
     */
    override fun supportsTestTemplate(context: ExtensionContext?): Boolean {
        return true
    }

    /**
     * Provides a stream of `TestTemplateInvocationContext` objects for a given test context.
     *
     * This method retrieves scenarios associated with a test method from the `SCENARIO_STORE`,
     * and creates a stream of [WarpDistributor] instances based on these scenarios.
     *
     * This solution allows for reusing the same test method for multiple scenarios,
     *
     * @param context The context for which the test template invocation contexts are provided.
     * @return A stream of `TestTemplateInvocationContext` instances.
     * @throws Exception if no scenarios are found for the given test method.
     */
    override fun provideTestTemplateInvocationContexts(
        context: ExtensionContext?
    ): Stream<TestTemplateInvocationContext> {
        val methodName: String = context?.requiredTestMethod?.name ?: throw NotFoundException.TestMethodName

        val objScenarios = SCENARIO_STORE[methodName]

        if (objScenarios !is SimulationGroup) throw NotFoundException.SimulationGroup(methodName)

        val anyIsolated = objScenarios.content.any { it.value.isolated }

        val filterFn = if (anyIsolated) ISOLATION_FILTER else DEFAULT_FILTER

        return objScenarios
            .content
            .asSequence()
            .filter(filterFn)
            .map { WarpDistributor(it, methodName) }
            .asStream()
    }

    companion object {
        // A store for simulation scenarios, keyed by test method names.
        val SCENARIO_STORE: MutableMap<String, SimulationGroup> = mutableMapOf()

        // A filter function that selects only isolated scenarios.
        val ISOLATION_FILTER: (Map.Entry<String, Scenario>) -> Boolean = { it.value.isolated }

        // A default filter function that selects all scenarios.
        val DEFAULT_FILTER: (Map.Entry<String, Scenario>) -> Boolean = { true }
    }
}
