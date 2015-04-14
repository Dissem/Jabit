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

    public static byte[] truncate(byte[] source, int size) {
        byte[] result = new byte[size];
        System.arraycopy(source, 0, result, 0, size);
        return result;
    }
}
