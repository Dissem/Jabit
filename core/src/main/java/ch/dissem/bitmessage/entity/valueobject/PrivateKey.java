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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Decode;
import ch.dissem.bitmessage.utils.Encode;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * Represents a private key. Additional information (stream, version, features, ...) is stored in the accompanying
 * {@link Pubkey} object.
 */
public class PrivateKey implements Streamable {
    private static final long serialVersionUID = 8562555470709110558L;

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

    public PrivateKey(BitmessageAddress address, String passphrase) {
        this(address.getVersion(), address.getStream(), passphrase);
    }

    public PrivateKey(long version, long stream, String passphrase) {
        this(new Builder(version, stream, false).seed(passphrase).generate());
    }

    private PrivateKey(Builder builder) {
        this.privateSigningKey = builder.privSK;
        this.privateEncryptionKey = builder.privEK;
        this.pubkey = Factory.createPubkey(builder.version, builder.stream, builder.pubSK, builder.pubEK, 0, 0);
    }

    private static class Builder {
        final long version;
        final long stream;
        final boolean shorter;

        byte[] seed;
        long nextNonce;

        byte[] privSK, privEK;
        byte[] pubSK, pubEK;

        private Builder(long version, long stream, boolean shorter) {
            this.version = version;
            this.stream = stream;
            this.shorter = shorter;
        }

        Builder seed(String passphrase) {
            try {
                seed = passphrase.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new ApplicationException(e);
            }
            return this;
        }

        Builder generate() {
            try {
                long signingKeyNonce = nextNonce;
                long encryptionKeyNonce = nextNonce + 1;
                byte[] ripe;
                do {
                    privEK = Bytes.truncate(security().sha512(seed, Encode.varInt(encryptionKeyNonce)), 32);
                    privSK = Bytes.truncate(security().sha512(seed, Encode.varInt(signingKeyNonce)), 32);
                    pubSK = security().createPublicKey(privSK);
                    pubEK = security().createPublicKey(privEK);
                    ripe = security().ripemd160(security().sha512(pubSK, pubEK));

                    signingKeyNonce += 2;
                    encryptionKeyNonce += 2;
                } while (ripe[0] != 0 || (shorter && ripe[1] != 0));
                nextNonce = signingKeyNonce;
            } catch (IOException e) {
                throw new ApplicationException(e);
            }
            return this;
        }
    }

    public static List<PrivateKey> deterministic(String passphrase, int numberOfAddresses, long version, long stream, boolean shorter) {
        List<PrivateKey> result = new ArrayList<>(numberOfAddresses);
        Builder builder = new Builder(version, stream, shorter).seed(passphrase);
        for (int i = 0; i < numberOfAddresses; i++) {
            builder.generate();
            result.add(new PrivateKey(builder));
        }
        return result;
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
