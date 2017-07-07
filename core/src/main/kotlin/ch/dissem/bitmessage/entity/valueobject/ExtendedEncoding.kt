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

package ch.dissem.bitmessage.entity.valueobject

import ch.dissem.msgpack.types.MPMap
import ch.dissem.msgpack.types.MPString
import ch.dissem.msgpack.types.MPType
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.zip.DeflaterOutputStream

/**
 * Extended encoding message object.
 */
data class ExtendedEncoding(val content: ExtendedEncoding.ExtendedType) : Serializable {

    val type: String? = content.type

    fun zip(): ByteArray {
        ByteArrayOutputStream().use { out ->
            DeflaterOutputStream(out).use { zipper -> content.pack().pack(zipper) }
            return out.toByteArray()
        }
    }

    interface Unpacker<out T : ExtendedType> {
        val type: String

        fun unpack(map: MPMap<MPString, MPType<*>>): T
    }

    interface ExtendedType : Serializable {
        val type: String

        fun pack(): MPMap<MPString, MPType<*>>
    }
}
