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

import ch.dissem.bitmessage.entity.payload.ObjectType

object SqlStrings {
    @JvmStatic fun join(vararg objects: Long): String {
        return objects.joinToString()
    }

    @JvmStatic fun join(vararg objects: ByteArray): String {
        return objects.map { Strings.hex(it) }.joinToString()
    }

    @JvmStatic fun join(vararg types: ObjectType): String {
        return types.map { it.number }.joinToString()
    }

    @JvmStatic fun join(vararg types: Enum<*>): String {
        return types.map { '\'' + it.name + '\'' }.joinToString()
    }
}
