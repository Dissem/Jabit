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

import java.io.*;

import static org.junit.Assert.assertEquals;

public class DecodeTest {
    @Test
    public void ensureDecodingWorks() throws Exception {
        // This should test all relevant cases for var_int and therefore also uint_16, uint_32 and int_64
        testCodec(0);
        for (long i = 1; i > 0; i = 3 * i + 7) {
            testCodec(i);
        }
    }

    private void testCodec(long number) throws IOException {
        ByteArrayOutputStream is = new ByteArrayOutputStream();
        Encode.varInt(number, is);
        assertEquals(number, Decode.varInt(new ByteArrayInputStream(is.toByteArray())));
    }
}
