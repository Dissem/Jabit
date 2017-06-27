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

import ch.dissem.bitmessage.entity.Streamable
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * This class handles encoding simple types from byte stream, according to
 * https://bitmessage.org/wiki/Protocol_specification#Common_structures
 */
object Encode {
    @JvmStatic fun varIntList(values: LongArray, stream: OutputStream) {
        varInt(values.size, stream)
        for (value in values) {
            varInt(value, stream)
        }
    }

    @JvmStatic fun varIntList(values: LongArray, buffer: ByteBuffer) {
        varInt(values.size, buffer)
        for (value in values) {
            varInt(value, buffer)
        }
    }

    @JvmStatic fun varInt(value: Int, buffer: ByteBuffer) = varInt(value.toLong(), buffer)
    @JvmStatic fun varInt(value: Long, buffer: ByteBuffer) {
        if (value < 0) {
            // This is due to the fact that Java doesn't really support unsigned values.
            // Please be aware that this might be an error due to a smaller negative value being cast to long.
            // Normally, negative values shouldn't occur within the protocol, and longs large enough for being
            // recognized as negatives aren't realistic.
            buffer.put(0xff.toByte())
            buffer.putLong(value)
        } else if (value < 0xfd) {
            buffer.put(value.toByte())
        } else if (value <= 0xffffL) {
            buffer.put(0xfd.toByte())
            buffer.putShort(value.toShort())
        } else if (value <= 0xffffffffL) {
            buffer.put(0xfe.toByte())
            buffer.putInt(value.toInt())
        } else {
            buffer.put(0xff.toByte())
            buffer.putLong(value)
        }
    }

    @JvmStatic fun varInt(value: Int) = varInt(value.toLong())
    @JvmStatic fun varInt(value: Long): ByteArray {
        val buffer = ByteBuffer.allocate(9)
        varInt(value, buffer)
        buffer.flip()
        return Bytes.truncate(buffer.array(), buffer.limit())
    }

    @JvmStatic @JvmOverloads fun varInt(value: Int, stream: OutputStream, counter: AccessCounter? = null) = varInt(value.toLong(), stream, counter)
    @JvmStatic @JvmOverloads fun varInt(value: Long, stream: OutputStream, counter: AccessCounter? = null) {
        val buffer = ByteBuffer.allocate(9)
        varInt(value, buffer)
        buffer.flip()
        stream.write(buffer.array(), 0, buffer.limit())
        AccessCounter.inc(counter, buffer.limit())
    }

    @JvmStatic @JvmOverloads fun int8(value: Long, stream: OutputStream, counter: AccessCounter? = null) = int8(value.toInt(), stream, counter)
    @JvmStatic @JvmOverloads fun int8(value: Int, stream: OutputStream, counter: AccessCounter? = null) {
        stream.write(value)
        AccessCounter.inc(counter)
    }

    @JvmStatic @JvmOverloads fun int16(value: Long, stream: OutputStream, counter: AccessCounter? = null) = int16(value.toShort(), stream, counter)
    @JvmStatic @JvmOverloads fun int16(value: Int, stream: OutputStream, counter: AccessCounter? = null) = int16(value.toShort(), stream, counter)
    @JvmStatic @JvmOverloads fun int16(value: Short, stream: OutputStream, counter: AccessCounter? = null) {
        stream.write(ByteBuffer.allocate(2).putShort(value).array())
        AccessCounter.inc(counter, 2)
    }

    @JvmStatic fun int16(value: Long, buffer: ByteBuffer) = int16(value.toShort(), buffer)
    @JvmStatic fun int16(value: Int, buffer: ByteBuffer) = int16(value.toShort(), buffer)
    @JvmStatic fun int16(value: Short, buffer: ByteBuffer) {
        buffer.putShort(value)
    }

    @JvmStatic @JvmOverloads fun int32(value: Long, stream: OutputStream, counter: AccessCounter? = null) = int32(value.toInt(), stream, counter)
    @JvmStatic @JvmOverloads fun int32(value: Int, stream: OutputStream, counter: AccessCounter? = null) {
        stream.write(ByteBuffer.allocate(4).putInt(value).array())
        AccessCounter.inc(counter, 4)
    }

    @JvmStatic fun int32(value: Long, buffer: ByteBuffer) = int32(value.toInt(), buffer)
    @JvmStatic fun int32(value: Int, buffer: ByteBuffer) {
        buffer.putInt(value)
    }

    @JvmStatic @JvmOverloads fun int64(value: Long, stream: OutputStream, counter: AccessCounter? = null) {
        stream.write(ByteBuffer.allocate(8).putLong(value).array())
        AccessCounter.inc(counter, 8)
    }

    @JvmStatic fun int64(value: Long, buffer: ByteBuffer) {
        buffer.putLong(value)
    }

    @JvmStatic fun varString(value: String, out: OutputStream) {
        val bytes = value.toByteArray(charset("utf-8"))
        // Technically, it says the length in characters, but I think this one might be correct.
        // It doesn't really matter, as only ASCII characters are being used.
        // see also Decode#varString()
        varInt(bytes.size.toLong(), out)
        out.write(bytes)
    }

    @JvmStatic fun varString(value: String, buffer: ByteBuffer) {
        val bytes = value.toByteArray()
        // Technically, it says the length in characters, but I think this one might be correct.
        // It doesn't really matter, as only ASCII characters are being used.
        // see also Decode#varString()
        buffer.put(varInt(bytes.size.toLong()))
        buffer.put(bytes)
    }

    @JvmStatic fun varBytes(data: ByteArray, out: OutputStream) {
        varInt(data.size.toLong(), out)
        out.write(data)
    }

    @JvmStatic fun varBytes(data: ByteArray, buffer: ByteBuffer) {
        varInt(data.size.toLong(), buffer)
        buffer.put(data)
    }

    /**
     * Serializes a [Streamable] object and returns the byte array.
     * @param streamable the object to be serialized
     * @return an array of bytes representing the given streamable object.
     */
    @JvmStatic fun bytes(streamable: Streamable): ByteArray {
        val stream = ByteArrayOutputStream()
        streamable.write(stream)
        return stream.toByteArray()
    }

    /**
     * @param streamable the object to be serialized
     * @param padding    the result will be padded such that its length is a multiple of *padding*
     * @return the bytes of the given [Streamable] object, 0-padded such that the final length is x*padding.
     */
    @JvmStatic fun bytes(streamable: Streamable, padding: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        streamable.write(stream)
        val offset = padding - stream.size() % padding
        val length = stream.size() + offset
        val result = ByteArray(length)
        stream.write(result, offset, stream.size())
        return result
    }
}
