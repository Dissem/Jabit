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

import java.util.Arrays;

/**
 * Created by chris on 20.07.15.
 */
public class Points {
    public static byte[] getX(byte[] P) {
        return Arrays.copyOfRange(P, 1, ((P.length - 1) / 2) + 1);
    }

    public static byte[] getY(byte[] P) {
        return Arrays.copyOfRange(P, ((P.length - 1) / 2) + 1, P.length);
    }
}
