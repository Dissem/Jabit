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

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Base64Test {
    @Test
    fun `ensure data is encoded and decoded correctly`() {
        val cryptography = BouncyCryptography()
        for (i in 100..200) {
            val data = cryptography.randomBytes(i)
            val string = Base64.encodeToString(data)
            val decoded = Base64.decode(string)
            assertEquals(data, decoded)
        }
    }
}
