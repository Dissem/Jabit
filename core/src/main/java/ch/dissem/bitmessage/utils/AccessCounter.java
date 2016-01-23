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
 * Intended to count the bytes read or written during (de-)serialization.
 */
public class AccessCounter {
    private int count;

    /**
     * Increases the counter by one, if not null.
     */
    public static void inc(AccessCounter counter) {
        if (counter != null) counter.inc();
    }

    /**
     * Increases the counter by length, if not null.
     */
    public static void inc(AccessCounter counter, int length) {
        if (counter != null) counter.inc(length);
    }

    /**
     * Increases the counter by one.
     */
    private void inc() {
        count++;
    }

    /**
     * Increases the counter by length.
     */
    private void inc(int length) {
        count += length;
    }

    public int length() {
        return count;
    }

    @Override
    public String toString() {
        return String.valueOf(count);
    }
}
