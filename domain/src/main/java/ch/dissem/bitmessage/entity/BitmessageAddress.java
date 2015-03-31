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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Encode;
import ch.dissem.bitmessage.utils.Security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static ch.dissem.bitmessage.utils.Security.ripemd160;
import static ch.dissem.bitmessage.utils.Security.sha512;

/**
 * A Bitmessage address. Can be a user's private address, an address string without public keys or a recipient's address
 * holding private keys.
 */
public abstract class BitmessageAddress {
    private long version;
    private long streamNumber;

    private Pubkey pubkey;

    public BitmessageAddress(Pubkey pubkey) {
        this.pubkey = pubkey;
    }

    public BitmessageAddress(String address) {
        Base58.decode(address.substring(3));
    }

    @Override
    public String toString() {
        try {
            byte[] combinedKeys = new byte[pubkey.getSigningKey().length + pubkey.getEncryptionKey().length];
            System.arraycopy(pubkey.getSigningKey(), 0, combinedKeys, 0, pubkey.getSigningKey().length);
            System.arraycopy(pubkey.getEncryptionKey(), 0, combinedKeys, pubkey.getSigningKey().length, pubkey.getEncryptionKey().length);

            byte[] hash = ripemd160(sha512(combinedKeys));

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Encode.varInt(version, stream);
            Encode.varInt(streamNumber, stream);
            stream.write(hash);

            byte[] checksum = Security.doubleSha512(stream.toByteArray());
            for (int i = 0; i < 4; i++) {
                stream.write(checksum[i]);
            }
            return "BM-" + Base58.encode(stream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
