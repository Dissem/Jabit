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
    public static byte[] shortVarBytes(InputStream stream, AccessCounter counter) throws IOException {
        int length = uint16(stream, counter);
        return bytes(stream, length, counter);
    }

    public static byte[] varBytes(InputStream stream) throws IOException {
        int length = (int) varInt(stream, null);
        return bytes(stream, length, null);
    }

    public static byte[] varBytes(InputStream stream, AccessCounter counter) throws IOException {
        int length = (int) varInt(stream, counter);
        return bytes(stream, length, counter);
    }

    public static byte[] bytes(InputStream stream, int count) throws IOException {
        return bytes(stream, count, null);
    }

    public static byte[] bytes(InputStream stream, int count, AccessCounter counter) throws IOException {
        byte[] result = new byte[count];
        int off = 0;
        while (off < count) {
            int read = stream.read(result, off, count - off);
            if (read < 0) {
                throw new IOException("Unexpected end of stream, wanted to read " + count + " bytes but only got " + off);
            }
            off += read;
        }
        inc(counter, count);
        return result;
    }

    public static long[] varIntList(InputStream stream) throws IOException {
        int length = (int) varInt(stream);
        long[] result = new long[length];

        for (int i = 0; i < length; i++) {
            result[i] = varInt(stream);
        }
        return result;
    }

    public static long varInt(InputStream stream) throws IOException {
        return varInt(stream, null);
    }

    public static long varInt(InputStream stream, AccessCounter counter) throws IOException {
        int first = stream.read();
        inc(counter);
        switch (first) {
            case 0xfd:
                return uint16(stream, counter);
            case 0xfe:
                return uint32(stream, counter);
            case 0xff:
                return int64(stream, counter);
            default:
                return first;
        }
    }

    public static int uint8(InputStream stream) throws IOException {
        return stream.read();
    }

    public static int uint16(InputStream stream) throws IOException {
        return uint16(stream, null);
    }

    public static int uint16(InputStream stream, AccessCounter counter) throws IOException {
        inc(counter, 2);
        return stream.read() * 256 + stream.read();
    }

    public static long uint32(InputStream stream) throws IOException {
        return uint32(stream, null);
    }

    public static long uint32(InputStream stream, AccessCounter counter) throws IOException {
        inc(counter, 4);
        return stream.read() * 16777216L + stream.read() * 65536L + stream.read() * 256L + stream.read();
    }

    public static long uint32(ByteBuffer buffer) {
        return buffer.get() * 16777216L + buffer.get() * 65536L + buffer.get() * 256L + buffer.get();
    }

    public static int int32(InputStream stream) throws IOException {
        return int32(stream, null);
    }

    public static int int32(InputStream stream, AccessCounter counter) throws IOException {
        inc(counter, 4);
        return ByteBuffer.wrap(bytes(stream, 4)).getInt();
    }

    public static long int64(InputStream stream) throws IOException {
        return int64(stream, null);
    }

    public static long int64(InputStream stream, AccessCounter counter) throws IOException {
        inc(counter, 8);
        return ByteBuffer.wrap(bytes(stream, 8)).getLong();
    }

    public static String varString(InputStream stream) throws IOException {
        return varString(stream, null);
    }

    public static String varString(InputStream stream, AccessCounter counter) throws IOException {
        int length = (int) varInt(stream, counter);
        // FIXME: technically, it says the length in characters, but I think this one might be correct
        // otherwise it will get complicated, as we'll need to read UTF-8 char by char...
        return new String(bytes(stream, length, counter), "utf-8");
    }
}
