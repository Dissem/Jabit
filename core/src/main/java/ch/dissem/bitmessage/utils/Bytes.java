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

package ch.dissem.bitmessage.utils;

/**
 * A helper class for working with byte arrays interpreted as unsigned big endian integers.
 * This is one part due to the fact that Java doesn't support unsigned numbers, and another
 * part so we don't have to convert between byte arrays and numbers in time critical
 * situations.
 * <p>
 * Note: This class can't yet be ported to Kotlin, as with Kotlin byte + byte = int, which
 * would be rather inefficient in our case.
 * </p>
 */
public class Bytes {
    public static final byte BYTE_0x80 = (byte) 0x80;

    public static void inc(byte[] nonce) {
        for (int i = nonce.length - 1; i >= 0; i--) {
            nonce[i]++;
            if (nonce[i] != 0) break;
        }
    }

    /**
     * Increases nonce by value, which is used as an unsigned byte value.
     *
     * @param nonce an unsigned number
     * @param value to be added to nonce
     */
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
     * @return true if a &lt; b.
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
     * @return true if a &lt; b, where the first [size] bytes are used as the numbers to check.
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
        return (a ^ BYTE_0x80) < (b ^ BYTE_0x80);
    }

    /**
     * @return a new byte array of length, left-padded with '0'.
     */
    public static byte[] expand(byte[] source, int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, size - source.length, source.length);
        return result;
    }

    /**
     * @return a new byte array containing the first <em>size</em> bytes of the given array.
     */
    public static byte[] truncate(byte[] source, int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, 0, size);
        return result;
    }

    /**
     * @return the byte array that hex represents. This is meant for test use and should be rewritten if used in
     * production code.
     */
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

    /**
     * @return the number of leading '0' of a byte array.
     */
    public static int numberOfLeadingZeros(byte[] bytes) {
        int i;
        for (i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0) return i;
        }
        return i;
    }
}
