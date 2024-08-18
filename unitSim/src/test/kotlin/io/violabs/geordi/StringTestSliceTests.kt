package io.violabs.geordi

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StringTestSliceTests {

    @Nested
    inner class ExpectJsonBlockingTest : UnitSim(json = Json) {
        @Test
        fun `expectJson will return a string in a compressed format`() = test {
            expectJson {
                """
                    {
                        "firstItem": "firstValue",
                        "nestedObject": {
                            "nestedItem": "nestedValue"
                        },
                        "array": [
                            "item1",
                            "item2"
                        ]
                    }
                """.trimIndent()
            }

            whenever {
                """{"firstItem":"firstValue","nestedObject":{"nestedItem":"nestedValue"},"array":["item1","item2"]}"""
            }
        }
    }

    @Nested
    inner class WheneverJsonBlockingTest : UnitSim(json = Json) {
        @Test
        fun `wheneverJson will return a string in a compressed format`() = test {
            expect {
                """{"firstItem":"firstValue","nestedObject":{"nestedItem":"nestedValue"},"array":["item1","item2"]}"""
            }

            wheneverJson {
                """
                    {
                        "firstItem": "firstValue",
                        "nestedObject": {
                            "nestedItem": "nestedValue"
                        },
                        "array": [
                            "item1",
                            "item2"
                        ]
                    }
                """.trimIndent()
            }
        }
    }

    @Nested
    inner class CoExpectJsonReactiveTest : CoUnitSim(json = Json) {
        @Test
        fun `coExpectJson will return a string in a compressed format`() = testBlocking {
            coExpectJson {
                """
                    {
                        "firstItem": "firstValue",
                        "nestedObject": {
                            "nestedItem": "nestedValue"
                        },
                        "array": [
                            "item1",
                            "item2"
                        ]
                    }
                """.trimIndent()
            }

            whenever {
                """{"firstItem":"firstValue","nestedObject":{"nestedItem":"nestedValue"},"array":["item1","item2"]}"""
            }
        }
    }

    @Nested
    inner class CoWheneverJsonReactiveTest : CoUnitSim(json = Json) {
        @Test
        fun `coWheneverJson will return a string in a compressed format`() = testBlocking {
            expect {
                """{"firstItem":"firstValue","nestedObject":{"nestedItem":"nestedValue"},"array":["item1","item2"]}"""
            }

            coWheneverJson {
                """
                    {
                        "firstItem": "firstValue",
                        "nestedObject": {
                            "nestedItem": "nestedValue"
                        },
                        "array": [
                            "item1",
                            "item2"
                        ]
                    }
                """.trimIndent()
            }
        }
    }
}
