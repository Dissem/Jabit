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

import ch.dissem.bitmessage.utils.Decode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used for person-to-person messages.
 */
public class Msg extends ObjectPayload {
    private long stream;
    private byte[] encrypted;
    private UnencryptedMessage unencrypted;

    private Msg(long stream, byte[] encrypted) {
        this.stream = stream;
        this.encrypted = encrypted;
    }

    public Msg(UnencryptedMessage unencrypted) {
        this.stream = unencrypted.getStream();
        this.unencrypted = unencrypted;
    }

    public static Msg read(InputStream is, long stream, int length) throws IOException {
        return new Msg(stream, Decode.bytes(is, length));
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
        return unencrypted != null;
    }

    @Override
    public void writeBytesToSign(OutputStream out) throws IOException {
        unencrypted.write(out, false);
    }

    @Override
    public byte[] getSignature() {
        return unencrypted.getSignature();
    }

    @Override
    public void setSignature(byte[] signature) {
        unencrypted.setSignature(signature);
    }

    public byte[] getEncrypted() {
        if (encrypted == null) {
            // TODO encrypt
        }
        return encrypted;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        stream.write(getEncrypted());
    }
}
