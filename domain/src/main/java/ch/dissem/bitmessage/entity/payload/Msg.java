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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used for person-to-person messages.
 */
public class Msg extends ObjectPayload {
    private long stream;
    private CryptoBox encrypted;
    private UnencryptedMessage decrypted;

    private Msg(long stream, CryptoBox encrypted) {
        this.stream = stream;
        this.encrypted = encrypted;
    }

    public Msg(UnencryptedMessage unencrypted, Pubkey publicKey) {
        this.stream = unencrypted.getStream();
        this.decrypted = unencrypted;
        this.encrypted = new CryptoBox(unencrypted, publicKey.getEncryptionKey());
    }

    public static Msg read(InputStream in, long stream, int length) throws IOException {
        return new Msg(stream, CryptoBox.read(in, length));
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
        return decrypted != null;
    }

    @Override
    public void writeBytesToSign(OutputStream out) throws IOException {
        decrypted.write(out, false);
    }

    @Override
    public byte[] getSignature() {
        return decrypted.getSignature();
    }

    @Override
    public void setSignature(byte[] signature) {
        decrypted.setSignature(signature);
    }

    public void decrypt(byte[] privateKey) throws IOException {
        decrypted = UnencryptedMessage.read(encrypted.decrypt(privateKey));
    }

    @Override
    public void write(OutputStream out) throws IOException {
        encrypted.write(out);
    }
}
