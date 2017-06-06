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

import org.junit.Test

import java.util.LinkedList

import org.junit.Assert.assertEquals

class CollectionsTest {
    @Test
    fun `ensure select random returns maximum possible items`() {
        val list = LinkedList<Int>()
        list += 0..9
        assertEquals(9, Collections.selectRandom(9, list).size.toLong())
    }
}
