package io.violabs.geordi.exceptions

/**
 * Exception thrown when a resource is not found.
 */
sealed class NotFoundException(message: String) : Exception(message) {
    class File(filename: String) : NotFoundException("File not found: $filename")
    class SimulationGroup(methodName: String) : NotFoundException("No scenarios found for method $methodName")

    @Suppress("UnusedPrivateMember")
    data object JsonMapper : NotFoundException("JSON mapper not found") {
        private fun readResolve(): Any = JsonMapper
    }

    @Suppress("UnusedPrivateMember")
    data object TestMethodName : NotFoundException("Test method name not found") {
        private fun readResolve(): Any = TestMethodName
    }
}
