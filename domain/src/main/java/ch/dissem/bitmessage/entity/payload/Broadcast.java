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
import ch.dissem.bitmessage.exception.DecryptionFailedException;

import java.io.IOException;

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 * Broadcasts are version 4 or 5.
 */
public abstract class Broadcast extends ObjectPayload implements Encrypted {
    protected final long stream;
    protected CryptoBox encrypted;
    protected Plaintext plaintext;

    protected Broadcast(long stream, CryptoBox encrypted, Plaintext plaintext) {
        this.stream = stream;
        this.encrypted = encrypted;
        this.plaintext = plaintext;
    }

    @Override
    public long getStream() {
        return stream;
    }

    public Plaintext getPlaintext() {
        return plaintext;
    }

    @Override
    public void encrypt(byte[] publicKey) throws IOException {
        this.encrypted = new CryptoBox(plaintext, publicKey);
    }

    @Override
    public void decrypt(byte[] privateKey) throws IOException, DecryptionFailedException {
        plaintext = Plaintext.read(encrypted.decrypt(privateKey));
    }

    @Override
    public boolean isDecrypted() {
        return plaintext != null;
    }
}
