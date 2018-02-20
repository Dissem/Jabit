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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DecodeTest {
    @Test
    fun `ensure decoding works`() {
        // This should test all relevant cases for var_int and therefore also uint_16, uint_32 and int_64
        testCodec(0)
        var i: Long = 1
        while (i > 0) {
            testCodec(i)
            i = 3 * i + 7
        }
    }

    private fun testCodec(number: Long) {
        val out = ByteArrayOutputStream()
        Encode.varInt(number, out)
        assertEquals(number, Decode.varInt(ByteArrayInputStream(out.toByteArray())))
    }
}
