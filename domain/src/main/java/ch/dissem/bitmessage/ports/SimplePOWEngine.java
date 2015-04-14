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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.utils.Bytes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static ch.dissem.bitmessage.utils.Bytes.inc;

/**
 * Created by chris on 14.04.15.
 */
public class SimplePOWEngine implements ProofOfWorkEngine {
    @Override
    public byte[] calculateNonce(byte[] initialHash, byte[] target, long nonceTrialsPerByte, long extraBytes) {
        byte[] nonce = new byte[8];
        MessageDigest mda;
        try {
            mda = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        do {
            inc(nonce);
            mda.update(nonce);
            mda.update(initialHash);
        } while (Bytes.lt(target, mda.digest(mda.digest()), 8));
        return nonce;
    }
}
