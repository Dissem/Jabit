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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static ch.dissem.bitmessage.utils.AccessCounter.inc;

/**
 * This class handles decoding simple types from byte stream, according to
 * https://bitmessage.org/wiki/Protocol_specification#Common_structures
 */
public class Decode {
    public static byte[] shortVarBytes(InputStream in, AccessCounter counter) throws IOException {
        int length = uint16(in, counter);
        return bytes(in, length, counter);
    }

    public static byte[] varBytes(InputStream in) throws IOException {
        return varBytes(in, null);
    }

    public static byte[] varBytes(InputStream in, AccessCounter counter) throws IOException {
        int length = (int) varInt(in, counter);
        return bytes(in, length, counter);
    }

    public static byte[] bytes(InputStream in, int count) throws IOException {
        return bytes(in, count, null);
    }

    public static byte[] bytes(InputStream in, int count, AccessCounter counter) throws IOException {
        byte[] result = new byte[count];
        int off = 0;
        while (off < count) {
            int read = in.read(result, off, count - off);
            if (read < 0) {
                throw new IOException("Unexpected end of stream, wanted to read " + count + " bytes but only got " + off);
            }
            off += read;
        }
        inc(counter, count);
        return result;
    }

    public static long[] varIntList(InputStream in) throws IOException {
        int length = (int) varInt(in);
        long[] result = new long[length];

        for (int i = 0; i < length; i++) {
            result[i] = varInt(in);
        }
        return result;
    }

    public static long varInt(InputStream in) throws IOException {
        return varInt(in, null);
    }

    public static long varInt(InputStream in, AccessCounter counter) throws IOException {
        int first = in.read();
        inc(counter);
        switch (first) {
            case 0xfd:
                return uint16(in, counter);
            case 0xfe:
                return uint32(in, counter);
            case 0xff:
                return int64(in, counter);
            default:
                return first;
        }
    }

    public static int uint8(InputStream in) throws IOException {
        return in.read();
    }

    public static int uint16(InputStream in) throws IOException {
        return uint16(in, null);
    }

    public static int uint16(InputStream in, AccessCounter counter) throws IOException {
        inc(counter, 2);
        return in.read() << 8 | in.read();
    }

    public static long uint32(InputStream in) throws IOException {
        return uint32(in, null);
    }

    public static long uint32(InputStream in, AccessCounter counter) throws IOException {
        inc(counter, 4);
        return in.read() << 24 | in.read() << 16 | in.read() << 8 | in.read();
    }

    public static long uint32(ByteBuffer in) {
        return u(in.get()) << 24 | u(in.get()) << 16 | u(in.get()) << 8 | u(in.get());
    }

    public static int int32(InputStream in) throws IOException {
        return int32(in, null);
    }

    public static int int32(InputStream in, AccessCounter counter) throws IOException {
        inc(counter, 4);
        return ByteBuffer.wrap(bytes(in, 4)).getInt();
    }

    public static long int64(InputStream in) throws IOException {
        return int64(in, null);
    }

    public static long int64(InputStream in, AccessCounter counter) throws IOException {
        inc(counter, 8);
        return ByteBuffer.wrap(bytes(in, 8)).getLong();
    }

    public static String varString(InputStream in) throws IOException {
        return varString(in, null);
    }

    public static String varString(InputStream in, AccessCounter counter) throws IOException {
        int length = (int) varInt(in, counter);
        // technically, it says the length in characters, but I think this one might be correct
        // otherwise it will get complicated, as we'll need to read UTF-8 char by char...
        return new String(bytes(in, length, counter), "utf-8");
    }

    /**
     * Returns the given byte as if it were unsigned.
     */
    private static int u(byte b) {
        return b & 0xFF;
    }
}
