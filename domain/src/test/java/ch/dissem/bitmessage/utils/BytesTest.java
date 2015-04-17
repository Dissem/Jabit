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

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by chris on 10.04.15.
 */
public class BytesTest {
    public static final Random rnd = new Random();

    @Test
    public void testIncrement() throws IOException {
        byte[] bytes = {0, -1};
        Bytes.inc(bytes);
        assertArrayEquals(TestUtils.int16(256), bytes);
    }

    @Test
    public void testIncrementByValue() throws IOException {
        for (int v = 0; v < 256; v++) {
            for (int i = 1; i < 256; i++) {
                byte[] bytes = {0, (byte) v};
                Bytes.inc(bytes, (byte) i);
                assertArrayEquals("value = " + v + "; inc = " + i + "; expected = " + (v + i), TestUtils.int16(v + i), bytes);
            }
        }
    }

    @Test
    public void testLowerThan() {
        for (int i = 0; i < 1000; i++) {
            BigInteger a = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            BigInteger b = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            System.out.println("a = " + a.toString(16) + "\tb = " + b.toString(16));
            assertEquals(a.compareTo(b) == -1, Bytes.lt(a.toByteArray(), b.toByteArray()));
        }
    }

    @Test
    public void testLowerThanBounded() {
        for (int i = 0; i < 1000; i++) {
            BigInteger a = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            BigInteger b = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            System.out.println("a = " + a.toString(16) + "\tb = " + b.toString(16));
            assertEquals(a.compareTo(b) == -1, Bytes.lt(
                    Bytes.expand(a.toByteArray(), 100),
                    Bytes.expand(b.toByteArray(), 100),
                    100));
        }
    }
}
