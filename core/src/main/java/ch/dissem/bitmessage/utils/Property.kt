/*
 * Copyright 2017 Christian Basler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.utils

/**
 * Some property that has a name, a value and/or other properties. This can be used for any purpose, but is for now
 * used to contain different status information. It is by default displayed in some JSON inspired human readable
 * notation, but you might only want to rely on the 'human readable' part.
 *
 *
 * If you need a real JSON representation, please add a method `toJson()`.
 *
 */
class Property private constructor(val name: String, val value: Any? = null, val properties: Array<Property> = emptyArray()) {

    constructor(name: String, value: Any) : this(name = name, value = value, properties = emptyArray())
    constructor(name: String, vararg properties: Property) : this(name, null, Array(properties.size, { i -> properties[i] }))

    /**
     * Returns the property if available or `null` otherwise.
     * Subproperties can be requested by submitting the sequence of properties.
     */
    fun getProperty(vararg name: String): Property? {
        properties
            .filter { name[0] == it.name }
            .forEach {
                if (name.size == 1)
                    return it
                else
                    return it.getProperty(*name.sliceArray(1..name.size - 1))
            }
        return null
    }

    override fun toString(): String {
        return toString("")
    }

    private fun toString(indentation: String): String {
        val result = StringBuilder()
        result.append(indentation).append(name).append(": ")
        if (value != null || properties.isEmpty()) {
            result.append(value)
        }
        if (properties.isNotEmpty()) {
            result.append("{\n")
            for (property in properties) {
                result.append(property.toString(indentation + "  ")).append('\n')
            }
            result.append(indentation).append("}")
        }
        return result.toString()
    }
}
