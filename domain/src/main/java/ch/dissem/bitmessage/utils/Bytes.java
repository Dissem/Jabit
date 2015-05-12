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

import ch.dissem.bitmessage.entity.Streamable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A helper class for working with byte arrays interpreted as unsigned big endian integers.
 */
public class Bytes {
    public static void inc(byte[] nonce) {
        for (int i = nonce.length - 1; i >= 0; i--) {
            nonce[i]++;
            if (nonce[i] != 0) break;
        }
    }

    public static void inc(byte[] nonce, byte value) {
        int i = nonce.length - 1;
        nonce[i] += value;
        if (value > 0 && (nonce[i] < 0 || nonce[i] >= value))
            return;
        if (value < 0 && (nonce[i] < 0 && nonce[i] >= value))
            return;

        for (i = i - 1; i >= 0; i--) {
            nonce[i]++;
            if (nonce[i] != 0) break;
        }
    }

    /**
     * Returns true if a < b.
     */
    public static boolean lt(byte[] a, byte[] b) {
        byte[] max = (a.length > b.length ? a : b);
        byte[] min = (max == a ? b : a);
        int diff = max.length - min.length;

        for (int i = 0; i < max.length - min.length; i++) {
            if (max[i] != 0) return a != max;
        }
        for (int i = diff; i < max.length; i++) {
            if (max[i] != min[i - diff]) {
                return lt(max[i], min[i - diff]) == (a == max);
            }
        }
        return false;
    }

    /**
     * Returns true if a < b, where the first [size] bytes are checked.
     */
    public static boolean lt(byte[] a, byte[] b, int size) {
        for (int i = 0; i < size; i++) {
            if (a[i] != b[i]) {
                return lt(a[i], b[i]);
            }
        }
        return false;
    }

    private static boolean lt(byte a, byte b) {
        if (a < 0) return b < 0 && a < b;
        if (b < 0) return a >= 0 || a < b;
        return a < b;
    }

    public static byte[] expand(byte[] source, int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, size - source.length, source.length);
        return result;
    }

    /**
     * Returns a new byte array containing the first <em>size</em> bytes of the given array.
     */
    public static byte[] truncate(byte[] source, int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, 0, size);
        return result;
    }

    public static byte[] subArray(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }

    public static byte[] concatenate(byte first, byte[] bytes) {
        byte[] result = new byte[bytes.length + 1];
        result[0] = first;
        System.arraycopy(bytes, 0, result, 1, bytes.length);
        return result;
    }

    public static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("expected even number of characters");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (hexValue(hex.charAt(i * 2)) * 16);
            result[i] += hexValue(hex.charAt(i * 2 + 1));
        }
        return result;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + c - 'a';
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + c - 'A';
        }
        throw new IllegalArgumentException("'" + c + "' is not a valid hex value");
    }

    public static int numberOfLeadingZeros(byte[] bytes) {
        int i;
        for (i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0) return i;
        }
        return i;
    }

    public static byte[] stripLeadingZeros(byte[] bytes) {
        return Arrays.copyOfRange(bytes, numberOfLeadingZeros(bytes), bytes.length);
    }

    public static byte[] from(Streamable data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
