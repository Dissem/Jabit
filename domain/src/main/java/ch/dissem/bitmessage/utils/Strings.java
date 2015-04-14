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
 * Created by chris on 13.04.15.
 */
public class Strings {
    public static CharSequence join(byte[]... objects) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(hex(objects[i]));
        }
        return streamList;
    }

    public static CharSequence join(long... objects) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(objects[i]);
        }
        return streamList;
    }

    public static CharSequence join(Object... objects) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(objects[i]);
        }
        return streamList;
    }

    public static CharSequence hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length + 2);
        hex.append("0x");
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex;
    }
}
