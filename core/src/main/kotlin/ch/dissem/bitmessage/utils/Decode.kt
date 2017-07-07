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

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * This class handles decoding simple types from byte stream, according to
 * https://bitmessage.org/wiki/Protocol_specification#Common_structures
 */
object Decode {
    @JvmStatic fun shortVarBytes(`in`: InputStream, counter: AccessCounter): ByteArray {
        val length = uint16(`in`, counter)
        return bytes(`in`, length, counter)
    }

    @JvmStatic @JvmOverloads fun varBytes(`in`: InputStream, counter: AccessCounter? = null): ByteArray {
        val length = varInt(`in`, counter).toInt()
        return bytes(`in`, length, counter)
    }

    @JvmStatic @JvmOverloads fun bytes(`in`: InputStream, count: Int, counter: AccessCounter? = null): ByteArray {
        val result = ByteArray(count)
        var off = 0
        while (off < count) {
            val read = `in`.read(result, off, count - off)
            if (read < 0) {
                throw IOException("Unexpected end of stream, wanted to read $count bytes but only got $off")
            }
            off += read
        }
        AccessCounter.inc(counter, count)
        return result
    }

    @JvmStatic fun varIntList(`in`: InputStream): LongArray {
        val length = varInt(`in`).toInt()
        val result = LongArray(length)

        for (i in 0..length - 1) {
            result[i] = varInt(`in`)
        }
        return result
    }

    @JvmStatic @JvmOverloads fun varInt(`in`: InputStream, counter: AccessCounter? = null): Long {
        val first = `in`.read()
        AccessCounter.inc(counter)
        when (first) {
            0xfd -> return uint16(`in`, counter).toLong()
            0xfe -> return uint32(`in`, counter)
            0xff -> return int64(`in`, counter)
            else -> return first.toLong()
        }
    }

    @JvmStatic fun uint8(`in`: InputStream): Int {
        return `in`.read()
    }

    @JvmStatic @JvmOverloads fun uint16(`in`: InputStream, counter: AccessCounter? = null): Int {
        AccessCounter.inc(counter, 2)
        return `in`.read() shl 8 or `in`.read()
    }

    @JvmStatic @JvmOverloads fun uint32(`in`: InputStream, counter: AccessCounter? = null): Long {
        AccessCounter.inc(counter, 4)
        return (`in`.read() shl 24 or (`in`.read() shl 16) or (`in`.read() shl 8) or `in`.read()).toLong()
    }

    @JvmStatic fun uint32(`in`: ByteBuffer): Long {
        return (u(`in`.get()) shl 24 or (u(`in`.get()) shl 16) or (u(`in`.get()) shl 8) or u(`in`.get())).toLong()
    }

    @JvmStatic @JvmOverloads fun int32(`in`: InputStream, counter: AccessCounter? = null): Int {
        AccessCounter.inc(counter, 4)
        return ByteBuffer.wrap(bytes(`in`, 4)).int
    }

    @JvmStatic @JvmOverloads fun int64(`in`: InputStream, counter: AccessCounter? = null): Long {
        AccessCounter.inc(counter, 8)
        return ByteBuffer.wrap(bytes(`in`, 8)).long
    }

    @JvmStatic @JvmOverloads fun varString(`in`: InputStream, counter: AccessCounter? = null): String {
        val length = varInt(`in`, counter).toInt()
        // technically, it says the length in characters, but I think this one might be correct
        // otherwise it will get complicated, as we'll need to read UTF-8 char by char...
        return String(bytes(`in`, length, counter))
    }

    /**
     * Returns the given byte as if it were unsigned.
     */
    @JvmStatic private fun u(b: Byte): Int {
        return b.toInt() and 0xFF
    }
}
