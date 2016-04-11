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

package ch.dissem.bitmessage.exception;

import ch.dissem.bitmessage.utils.Strings;

import java.io.IOException;
import java.util.Arrays;

public class InsufficientProofOfWorkException extends IOException {
    private static final long serialVersionUID = 9105580366564571318L;

    public InsufficientProofOfWorkException(byte[] target, byte[] hash) {
        super("Insufficient proof of work: " + Strings.hex(target) + " required, " + Strings.hex(Arrays.copyOfRange(hash, 0, 8)) + " achieved.");
    }
}
