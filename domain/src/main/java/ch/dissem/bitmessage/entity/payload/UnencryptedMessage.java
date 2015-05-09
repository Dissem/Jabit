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

import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.utils.Decode;
import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The unencrypted message to be sent by 'msg' or 'broadcast'.
 */
public class UnencryptedMessage implements Streamable {
    private final long addressVersion;
    private final long stream;
    private final int behaviorBitfield;
    private final byte[] publicSigningKey;
    private final byte[] publicEncryptionKey;
    private final long nonceTrialsPerByte;
    private final long extraBytes;
    private final long encoding;
    private final byte[] message;
    private byte[] signature;

    private UnencryptedMessage(Builder builder) {
        addressVersion = builder.addressVersion;
        stream = builder.stream;
        behaviorBitfield = builder.behaviorBitfield;
        publicSigningKey = builder.publicSigningKey;
        publicEncryptionKey = builder.publicEncryptionKey;
        nonceTrialsPerByte = builder.nonceTrialsPerByte;
        extraBytes = builder.extraBytes;
        encoding = builder.encoding;
        message = builder.message;
        signature = builder.signature;
    }

    public static UnencryptedMessage read(InputStream is) throws IOException {
        return new Builder()
                .addressVersion(Decode.varInt(is))
                .stream(Decode.varInt(is))
                .behaviorBitfield(Decode.int32(is))
                .publicSigningKey(Decode.bytes(is, 64))
                .publicEncryptionKey(Decode.bytes(is, 64))
                .nonceTrialsPerByte(Decode.varInt(is))
                .extraBytes(Decode.varInt(is))
                .encoding(Decode.varInt(is))
                .message(Decode.varBytes(is))
                .signature(Decode.varBytes(is))
                .build();
    }

    public long getStream() {
        return stream;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void write(OutputStream out, boolean includeSignature) throws IOException {
        Encode.varInt(addressVersion, out);
        Encode.varInt(stream, out);
        Encode.int32(behaviorBitfield, out);
        out.write(publicSigningKey);
        out.write(publicEncryptionKey);
        Encode.varInt(nonceTrialsPerByte, out);
        Encode.varInt(extraBytes, out);
        Encode.varInt(encoding, out);
        Encode.varInt(message.length, out);
        out.write(message);
        if (includeSignature) {
            Encode.varInt(signature.length, out);
            out.write(signature);
        }
    }

    @Override
    public void write(OutputStream out) throws IOException {
        write(out, true);
    }

    public static final class Builder {
        private long addressVersion;
        private long stream;
        private int behaviorBitfield;
        private byte[] publicSigningKey;
        private byte[] publicEncryptionKey;
        private long nonceTrialsPerByte;
        private long extraBytes;
        private long encoding;
        private byte[] message;
        private byte[] signature;

        public Builder() {
        }

        public Builder addressVersion(long addressVersion) {
            this.addressVersion = addressVersion;
            return this;
        }

        public Builder stream(long stream) {
            this.stream = stream;
            return this;
        }

        public Builder behaviorBitfield(int behaviorBitfield) {
            this.behaviorBitfield = behaviorBitfield;
            return this;
        }

        public Builder publicSigningKey(byte[] publicSigningKey) {
            this.publicSigningKey = publicSigningKey;
            return this;
        }

        public Builder publicEncryptionKey(byte[] publicEncryptionKey) {
            this.publicEncryptionKey = publicEncryptionKey;
            return this;
        }

        public Builder nonceTrialsPerByte(long nonceTrialsPerByte) {
            this.nonceTrialsPerByte = nonceTrialsPerByte;
            return this;
        }

        public Builder extraBytes(long extraBytes) {
            this.extraBytes = extraBytes;
            return this;
        }

        public Builder encoding(long encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder message(byte[] message) {
            this.message = message;
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public UnencryptedMessage build() {
            return new UnencryptedMessage(this);
        }
    }
}
