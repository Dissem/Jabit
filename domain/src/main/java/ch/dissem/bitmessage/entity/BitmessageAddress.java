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
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A Bitmessage address. Can be a user's private address, an address string without public keys or a recipient's address
 * holding private keys.
 */
public class BitmessageAddress {
    private long version;
    private long stream;
    private byte[] ripe;

    private String address;

    private PrivateKey privateKey;
    private Pubkey pubkey;

    private String alias;

    public BitmessageAddress(PrivateKey privateKey) {
        this.privateKey = privateKey;
        this.pubkey = privateKey.getPubkey();
        this.ripe = pubkey.getRipe();
        this.address = generateAddress();
    }

    public BitmessageAddress(String address) {
        try {
            byte[] bytes = Base58.decode(address.substring(3));
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            AccessCounter counter = new AccessCounter();
            this.version = Decode.varInt(in, counter);
            this.stream = Decode.varInt(in, counter);
            this.ripe = Decode.bytes(in, bytes.length - counter.length() - 4);
            testChecksum(Decode.bytes(in, 4), bytes);
            this.address = generateAddress();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void testChecksum(byte[] expected, byte[] address) {
        byte[] checksum = Security.doubleSha512(address, address.length - 4);
        for (int i = 0; i < 4; i++) {
            if (expected[i] != checksum[i]) throw new IllegalArgumentException("Checksum of address failed");
        }
    }

    private String generateAddress() {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Encode.varInt(version, os);
            Encode.varInt(stream, os);
            os.write(ripe);

            byte[] checksum = Security.doubleSha512(os.toByteArray());
            for (int i = 0; i < 4; i++) {
                os.write(checksum[i]);
            }
            return "BM-" + Base58.encode(os.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long getStream() {
        return stream;
    }

    public long getVersion() {
        return version;
    }

    public Pubkey getPubkey() {
        return pubkey;
    }

    public void setPubkey(Pubkey pubkey) {
        if (!Arrays.equals(ripe, pubkey.getRipe())) throw new IllegalArgumentException("Pubkey has incompatible RIPE");
        this.pubkey = pubkey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getAddress() {
        return address;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public String toString() {
        return alias != null ? alias : address;
    }

    public byte[] getRipe() {
        return ripe;
    }
}
