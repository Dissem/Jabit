/*
 * Copyright 2015 Christian Basler
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

package ch.dissem.bitmessage.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringsTest {
    @Test
    public void ensureJoinWorksWithLongArray() {
        long[] test = {1L, 2L};
        assertEquals("1, 2", Strings.join(test).toString());
    }

    @Test
    public void testHexString() {
        assertEquals("0x48656c6c6f21", Strings.hex("Hello!".getBytes()));
    }
}
