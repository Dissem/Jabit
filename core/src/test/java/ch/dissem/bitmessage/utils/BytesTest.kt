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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.math.BigInteger
import java.util.*

class BytesTest {
    val rnd = Random()

    @Test
    fun `ensure expands correctly`() {
        val source = byteArrayOf(1)
        val expected = byteArrayOf(0, 1)
        assertArrayEquals(expected, Bytes.expand(source, 2))
    }

    @Test
    fun `ensure increment carry works`() {
        val bytes = byteArrayOf(0, -1)
        Bytes.inc(bytes)
        assertArrayEquals(TestUtils.int16(256), bytes)
    }

    @Test
    fun `test increment by value`() {
        for (v in 0..255) {
            for (i in 1..255) {
                val bytes = byteArrayOf(0, v.toByte())
                Bytes.inc(bytes, i.toByte())
                assertArrayEquals("value = " + v + "; inc = " + i + "; expected = " + (v + i), TestUtils.int16(v + i), bytes)
            }
        }
    }

    /**
     * This test is used to compare different implementations of the single byte lt comparison. It an safely be ignored.
     */
    @Test
    @Ignore
    fun `test lower than single byte`() {
        val a = ByteArray(1)
        val b = ByteArray(1)
        for (i in 0..254) {
            for (j in 0..254) {
                println("a = $i\tb = $j")
                a[0] = i.toByte()
                b[0] = j.toByte()
                assertEquals(i < j, Bytes.lt(a, b))
            }
        }
    }

    @Test
    fun `test lower than`() {
        for (i in 0..999) {
            val a = BigInteger.valueOf(rnd.nextLong()).pow(rnd.nextInt(5) + 1).abs()
            val b = BigInteger.valueOf(rnd.nextLong()).pow(rnd.nextInt(5) + 1).abs()
            println("a = " + a.toString(16) + "\tb = " + b.toString(16))
            assertEquals(a.compareTo(b) == -1, Bytes.lt(a.toByteArray(), b.toByteArray()))
        }
    }

    @Test
    fun `test lower than bounded`() {
        for (i in 0..999) {
            val a = BigInteger.valueOf(rnd.nextLong()).pow(rnd.nextInt(5) + 1).abs()
            val b = BigInteger.valueOf(rnd.nextLong()).pow(rnd.nextInt(5) + 1).abs()
            println("a = " + a.toString(16) + "\tb = " + b.toString(16))
            assertEquals(a.compareTo(b) == -1, Bytes.lt(
                Bytes.expand(a.toByteArray(), 100),
                Bytes.expand(b.toByteArray(), 100),
                100))
        }
    }
}
