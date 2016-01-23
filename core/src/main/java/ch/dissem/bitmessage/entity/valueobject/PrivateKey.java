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

package ch.dissem.bitmessage.entity.valueobject;

import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Decode;
import ch.dissem.bitmessage.utils.Encode;

import java.io.*;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * Represents a private key. Additional information (stream, version, features, ...) is stored in the accompanying
 * {@link Pubkey} object.
 */
public class PrivateKey implements Streamable {
    public static final int PRIVATE_KEY_SIZE = 32;
    private final byte[] privateSigningKey;
    private final byte[] privateEncryptionKey;

    private final Pubkey pubkey;

    public PrivateKey(boolean shorter, long stream, long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
        byte[] privSK;
        byte[] pubSK;
        byte[] privEK;
        byte[] pubEK;
        byte[] ripe;
        do {
            privSK = security().randomBytes(PRIVATE_KEY_SIZE);
            privEK = security().randomBytes(PRIVATE_KEY_SIZE);
            pubSK = security().createPublicKey(privSK);
            pubEK = security().createPublicKey(privEK);
            ripe = Pubkey.getRipe(pubSK, pubEK);
        } while (ripe[0] != 0 || (shorter && ripe[1] != 0));
        this.privateSigningKey = privSK;
        this.privateEncryptionKey = privEK;
        this.pubkey = security().createPubkey(Pubkey.LATEST_VERSION, stream, privateSigningKey, privateEncryptionKey,
                nonceTrialsPerByte, extraBytes, features);
    }

    public PrivateKey(byte[] privateSigningKey, byte[] privateEncryptionKey, Pubkey pubkey) {
        this.privateSigningKey = privateSigningKey;
        this.privateEncryptionKey = privateEncryptionKey;
        this.pubkey = pubkey;
    }

    public PrivateKey(long version, long stream, String passphrase, long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
        try {
            // FIXME: this is most definitely wrong
            this.privateSigningKey = Bytes.truncate(security().sha512(passphrase.getBytes("UTF-8"), new byte[]{0}), 32);
            this.privateEncryptionKey = Bytes.truncate(security().sha512(passphrase.getBytes("UTF-8"), new byte[]{1}), 32);
            this.pubkey = security().createPubkey(version, stream, privateSigningKey, privateEncryptionKey,
                    nonceTrialsPerByte, extraBytes, features);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey read(InputStream is) throws IOException {
        int version = (int) Decode.varInt(is);
        long stream = Decode.varInt(is);
        int len = (int) Decode.varInt(is);
        Pubkey pubkey = Factory.readPubkey(version, stream, is, len, false);
        len = (int) Decode.varInt(is);
        byte[] signingKey = Decode.bytes(is, len);
        len = (int) Decode.varInt(is);
        byte[] encryptionKey = Decode.bytes(is, len);
        return new PrivateKey(signingKey, encryptionKey, pubkey);
    }

    public byte[] getPrivateSigningKey() {
        return privateSigningKey;
    }

    public byte[] getPrivateEncryptionKey() {
        return privateEncryptionKey;
    }

    public Pubkey getPubkey() {
        return pubkey;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        Encode.varInt(pubkey.getVersion(), out);
        Encode.varInt(pubkey.getStream(), out);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pubkey.writeUnencrypted(baos);
        Encode.varInt(baos.size(), out);
        out.write(baos.toByteArray());
        Encode.varInt(privateSigningKey.length, out);
        out.write(privateSigningKey);
        Encode.varInt(privateEncryptionKey.length, out);
        out.write(privateEncryptionKey);
    }
}
