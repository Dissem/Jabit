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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by chris on 10.04.15.
 */
public class BytesTest {
    @Test
    public void testIncrement() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Encode.int16(256, out);

        byte[] bytes = {0, -1};
        Bytes.inc(bytes);
        assertArrayEquals(out.toByteArray(), bytes);
    }

    @Test
    public void testLowerThan() {
        Random rnd = new Random();
        for (int i = 0; i < 1000; i++) {
            BigInteger a = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            BigInteger b = BigInteger.valueOf(rnd.nextLong()).pow((rnd.nextInt(5) + 1)).abs();
            System.out.println("a = " + a.toString(16) + "\tb = " + b.toString(16));
            assertEquals(a.compareTo(b) == -1, Bytes.lt(a.toByteArray(), b.toByteArray()));
        }
    }

    @Test
    public void testLowerThanBounded() {
        Random rnd = new Random();
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
