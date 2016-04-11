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

import static org.junit.Assert.assertEquals;

public class EncodeTest {
    @Test
    public void testUint8() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encode.int8(0, stream);
        checkBytes(stream, 0);

        stream = new ByteArrayOutputStream();
        Encode.int8(255, stream);
        checkBytes(stream, 255);
    }

    @Test
    public void testUint16() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encode.int16(0, stream);
        checkBytes(stream, 0, 0);

        stream = new ByteArrayOutputStream();
        Encode.int16(513, stream);
        checkBytes(stream, 2, 1);
    }

    @Test
    public void testUint32() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encode.int32(0, stream);
        checkBytes(stream, 0, 0, 0, 0);

        stream = new ByteArrayOutputStream();
        Encode.int32(67305985, stream);
        checkBytes(stream, 4, 3, 2, 1);

        stream = new ByteArrayOutputStream();
        Encode.int32(3355443201L, stream);
        checkBytes(stream, 200, 0, 0, 1);
    }

    @Test
    public void testUint64() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encode.int64(0, stream);
        checkBytes(stream, 0, 0, 0, 0, 0, 0, 0, 0);

        stream = new ByteArrayOutputStream();
        Encode.int64(578437695752307201L, stream);
        checkBytes(stream, 8, 7, 6, 5, 4, 3, 2, 1);

        stream = new ByteArrayOutputStream();
        // 200 * 72057594037927936L + 1
        Encode.int64(0xc800000000000001L, stream);
        checkBytes(stream, 200, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    public void testVarInt() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encode.varInt(0, stream);
        checkBytes(stream, 0);

        stream = new ByteArrayOutputStream();
        Encode.varInt(252, stream);
        checkBytes(stream, 252);

        stream = new ByteArrayOutputStream();
        Encode.varInt(253, stream);
        checkBytes(stream, 253, 0, 253);

        stream = new ByteArrayOutputStream();
        Encode.varInt(65535, stream);
        checkBytes(stream, 253, 255, 255);

        stream = new ByteArrayOutputStream();
        Encode.varInt(65536, stream);
        checkBytes(stream, 254, 0, 1, 0, 0);

        stream = new ByteArrayOutputStream();
        Encode.varInt(4294967295L, stream);
        checkBytes(stream, 254, 255, 255, 255, 255);

        stream = new ByteArrayOutputStream();
        Encode.varInt(4294967296L, stream);
        checkBytes(stream, 255, 0, 0, 0, 1, 0, 0, 0, 0);

        stream = new ByteArrayOutputStream();
        Encode.varInt(-1L, stream);
        checkBytes(stream, 255, 255, 255, 255, 255, 255, 255, 255, 255);
    }


    public void checkBytes(ByteArrayOutputStream stream, int... bytes) {
        assertEquals(bytes.length, stream.size());
        byte[] streamBytes = stream.toByteArray();

        for (int i = 0; i < bytes.length; i++) {
            assertEquals((byte) bytes[i], streamBytes[i]);
        }
    }
}
