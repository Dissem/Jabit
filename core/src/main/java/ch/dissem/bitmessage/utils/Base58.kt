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

import ch.dissem.bitmessage.exception.AddressFormatException
import java.util.Arrays.copyOfRange

/**
 * Base58 encoder and decoder.

 * @author Christian Basler: I removed some dependencies to the BitcoinJ code so it can be used here more easily.
 */
object Base58 {
    private val INDEXES = IntArray(128)
    private val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()

    init {
        for (i in INDEXES.indices) {
            INDEXES[i] = -1
        }
        for (i in ALPHABET.indices) {
            INDEXES[ALPHABET[i].toInt()] = i
        }
    }

    /**
     * Encodes the given bytes in base58. No checksum is appended.

     * @param data to encode
     * *
     * @return base58 encoded input
     */
    @JvmStatic fun encode(data: ByteArray): String {
        if (data.isEmpty()) {
            return ""
        }
        val bytes = copyOfRange(data, 0, data.size)
        // Count leading zeroes.
        var zeroCount = 0
        while (zeroCount < bytes.size && bytes[zeroCount].toInt() == 0) {
            ++zeroCount
        }
        // The actual encoding.
        val temp = ByteArray(bytes.size * 2)
        var j = temp.size

        var startAt = zeroCount
        while (startAt < bytes.size) {
            val mod = divmod58(bytes, startAt)
            if (bytes[startAt].toInt() == 0) {
                ++startAt
            }
            temp[--j] = ALPHABET[mod.toInt()].toByte()
        }

        // Strip extra '1' if there are some after decoding.
        while (j < temp.size && temp[j] == ALPHABET[0].toByte()) {
            ++j
        }
        // Add as many leading '1' as there were leading zeros.
        while (--zeroCount >= 0) {
            temp[--j] = ALPHABET[0].toByte()
        }

        val output = copyOfRange(temp, j, temp.size)
        return String(output, Charsets.US_ASCII)
    }

    @Throws(AddressFormatException::class)
    @JvmStatic fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }
        val input58 = ByteArray(input.length)
        // Transform the String to a base58 byte sequence
        for (i in 0..input.length - 1) {
            val c = input[i]

            var digit58 = -1
            if (c.toInt() < 128) {
                digit58 = INDEXES[c.toInt()]
            }
            if (digit58 < 0) {
                throw AddressFormatException("Illegal character $c at $i")
            }

            input58[i] = digit58.toByte()
        }
        // Count leading zeroes
        var zeroCount = 0
        while (zeroCount < input58.size && input58[zeroCount].toInt() == 0) {
            ++zeroCount
        }
        // The encoding
        val temp = ByteArray(input.length)
        var j = temp.size

        var startAt = zeroCount
        while (startAt < input58.size) {
            val mod = divmod256(input58, startAt)
            if (input58[startAt].toInt() == 0) {
                ++startAt
            }

            temp[--j] = mod
        }
        // Do no add extra leading zeroes, move j to first non null byte.
        while (j < temp.size && temp[j].toInt() == 0) {
            ++j
        }
        return copyOfRange(temp, j - zeroCount, temp.size)
    }

    //
    // number -> number / 58, returns number % 58
    //
    private fun divmod58(number: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt..number.size - 1) {
            val digit256 = number[i].toInt() and 0xFF
            val temp = remainder * 256 + digit256

            number[i] = (temp / 58).toByte()

            remainder = temp % 58
        }

        return remainder.toByte()
    }

    //
    // number -> number / 256, returns number % 256
    //
    private fun divmod256(number58: ByteArray, startAt: Int): Byte {
        var remainder = 0
        for (i in startAt..number58.size - 1) {
            val digit58 = number58[i].toInt() and 0xFF
            val temp = remainder * 58 + digit58

            number58[i] = (temp / 256).toByte()

            remainder = temp % 256
        }

        return remainder.toByte()
    }
}
