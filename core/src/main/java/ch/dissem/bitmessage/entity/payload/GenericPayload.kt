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

package ch.dissem.bitmessage.entity.payload

import ch.dissem.bitmessage.utils.Decode
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * In cases we don't know what to do with an object, we just store its bytes and send it again - we don't really
 * have to know what it is.
 */
class GenericPayload(version: Long, override val stream: Long, val data: ByteArray) : ObjectPayload(version) {

    override val type: ObjectType? = null

    override fun write(out: OutputStream) {
        out.write(data)
    }

    override fun write(buffer: ByteBuffer) {
        buffer.put(data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenericPayload) return false

        if (stream != other.stream) return false
        return Arrays.equals(data, other.data)
    }

    override fun hashCode(): Int {
        var result = (stream xor stream.ushr(32)).toInt()
        result = 31 * result + Arrays.hashCode(data)
        return result
    }

    companion object {
        fun read(version: Long, stream: Long, `is`: InputStream, length: Int): GenericPayload {
            return GenericPayload(version, stream, Decode.bytes(`is`, length))
        }
    }
}
