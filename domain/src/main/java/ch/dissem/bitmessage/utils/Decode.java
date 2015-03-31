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

/**
 * This class handles decoding simple types from byte stream, according to
 * https://bitmessage.org/wiki/Protocol_specification#Common_structures
 */
public class Decode {
    public static byte[] bytes(InputStream stream, int count) throws IOException {
        byte[] result = new byte[count];
        stream.read(result);
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
        int first = stream.read();
        switch (first) {
            case 0xfd:
                return uint16(stream);
            case 0xfe:
                return uint32(stream);
            case 0xff:
                return int64(stream);
            default:
                return first;
        }
    }

    public static int uint8(InputStream stream) throws IOException {
        return stream.read();
    }

    public static int uint16(InputStream stream) throws IOException {
        return stream.read() * 256 + stream.read();
    }

    public static long uint32(InputStream stream) throws IOException {
        return stream.read() * 16777216L + stream.read() * 65536L + stream.read() * 256L + stream.read();
    }

    public static int int32(InputStream stream) throws IOException {
        return ByteBuffer.wrap(bytes(stream, 4)).getInt();
    }

    public static long int64(InputStream stream) throws IOException {
        return ByteBuffer.wrap(bytes(stream, 8)).getLong();
    }

    public static String varString(InputStream stream) throws IOException {
        int length = (int) varInt(stream);
        // FIXME: technically, it says the length in characters, but I think this one might be correct
        byte[] bytes = new byte[length];
        // FIXME: I'm also not quite sure if this works, maybe the read return value needs to be handled properly
        stream.read(bytes);
        return new String(bytes, "utf-8");
    }
}
