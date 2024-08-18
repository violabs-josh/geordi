package io.violabs.geordi

import io.violabs.geordi.exceptions.NotFoundException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private typealias StringProperties = UnitSim.TestSlice<String>.DynamicProperties<String>
private typealias StringProvider = (props: StringProperties) -> String
private typealias CoStringProvider = suspend (props: StringProperties) -> String

/**
 * Takes in a json string and formats it to match the expected json format
 * @param givenFn a function that takes in a StringProperties object and returns a json string
 */
fun UnitSim.TestSlice<String>.expectJson(givenFn: StringProvider): Unit = expect {
    compressJson(givenFn)
}

/**
 * Takes in a json string and formats it to match the expected json format
 * @param whenFn a function that takes in a StringProperties object and returns a json string
 */
fun UnitSim.TestSlice<String>.wheneverJson(whenFn: StringProvider): Unit = whenever {
    compressJson(whenFn)
}

/**
 * Takes in a json string and formats it to match the expected json format
 * @param givenFn a function that takes in a StringProperties object and returns a json string
 */
fun CoUnitSim.CoTestSlice<String>.coExpectJson(givenFn: CoStringProvider): Unit = coExpect {
    compressJson(givenFn)
}

/**
 * Takes in a json string and formats it to match the expected json format
 * @param whenFn a function that takes in a StringProperties object and returns a json string
 */
fun CoUnitSim.CoTestSlice<String>.coWheneverJson(whenFn: CoStringProvider): Unit = coWhenever {
    compressJson(whenFn)
}

/**
 * Compresses a json string to match the expected json format.
 * @param stringProvider a function that takes in a StringProperties object and returns a json string
 * @return a compressed json string
 * @throws NotFoundException.JsonMapper if the json mapper is not found
 */
private fun UnitSim.TestSlice<String>.compressJson(stringProvider: StringProvider): String {
    val mapper: Json = json ?: throw NotFoundException.JsonMapper

    val mapObject = mapper.parseToJsonElement(stringProvider(objectProvider))
    return mapper.encodeToString(JsonObject.serializer(), mapObject.jsonObject)
}

/**
 * Compresses a json string to match the expected json format.
 * @param coStringProvider a function that takes in a StringProperties object and returns a json string
 * @return a compressed json string
 * @throws NotFoundException.JsonMapper if the json mapper is not found
 */
private suspend fun CoUnitSim.CoTestSlice<String>.compressJson(coStringProvider: CoStringProvider): String {
    val mapper: Json = json ?: throw NotFoundException.JsonMapper

    val mapObject = mapper.parseToJsonElement(coStringProvider(objectProvider))
    return mapper.encodeToString(JsonObject.serializer(), mapObject.jsonObject)
}
