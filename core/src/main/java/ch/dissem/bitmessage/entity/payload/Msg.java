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

package ch.dissem.bitmessage.entity.payload;

import ch.dissem.bitmessage.entity.Encrypted;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.PlaintextHolder;
import ch.dissem.bitmessage.exception.DecryptionFailedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;

/**
 * Used for person-to-person messages.
 */
public class Msg extends ObjectPayload implements Encrypted, PlaintextHolder {
    private static final long serialVersionUID = 4327495048296365733L;

    private long stream;
    private CryptoBox encrypted;
    private Plaintext plaintext;

    private Msg(long stream, CryptoBox encrypted) {
        super(1);
        this.stream = stream;
        this.encrypted = encrypted;
    }

    public Msg(Plaintext plaintext) {
        super(1);
        this.stream = plaintext.getStream();
        this.plaintext = plaintext;
    }

    public static Msg read(InputStream in, long stream, int length) throws IOException {
        return new Msg(stream, CryptoBox.read(in, length));
    }

    @Override
    public Plaintext getPlaintext() {
        return plaintext;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.MSG;
    }

    @Override
    public long getStream() {
        return stream;
    }

    @Override
    public boolean isSigned() {
        return true;
    }

    @Override
    public void writeBytesToSign(OutputStream out) throws IOException {
        plaintext.write(out, false);
    }

    @Override
    public byte[] getSignature() {
        return plaintext.getSignature();
    }

    @Override
    public void setSignature(byte[] signature) {
        plaintext.setSignature(signature);
    }

    @Override
    public void encrypt(byte[] publicKey) throws IOException {
        this.encrypted = new CryptoBox(plaintext, publicKey);
    }

    @Override
    public void decrypt(byte[] privateKey) throws IOException, DecryptionFailedException {
        plaintext = Plaintext.read(MSG, encrypted.decrypt(privateKey));
    }

    @Override
    public boolean isDecrypted() {
        return plaintext != null;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        if (encrypted == null) throw new IllegalStateException("Msg must be signed and encrypted before writing it.");
        encrypted.write(out);
    }
}
