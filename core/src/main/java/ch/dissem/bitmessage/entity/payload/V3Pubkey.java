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
import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A version 3 public key.
 */
public class V3Pubkey extends V2Pubkey {
    private static final long serialVersionUID = 6958853116648528319L;

    long nonceTrialsPerByte;
    long extraBytes;
    byte[] signature;

    protected V3Pubkey(long version, Builder builder) {
        super(version);
        stream = builder.streamNumber;
        behaviorBitfield = builder.behaviorBitfield;
        publicSigningKey = add0x04(builder.publicSigningKey);
        publicEncryptionKey = add0x04(builder.publicEncryptionKey);
        nonceTrialsPerByte = builder.nonceTrialsPerByte;
        extraBytes = builder.extraBytes;
        signature = builder.signature;
    }

    public static V3Pubkey read(InputStream is, long stream) throws IOException {
        return new V3Pubkey.Builder()
                .stream(stream)
                .behaviorBitfield(Decode.int32(is))
                .publicSigningKey(Decode.bytes(is, 64))
                .publicEncryptionKey(Decode.bytes(is, 64))
                .nonceTrialsPerByte(Decode.varInt(is))
                .extraBytes(Decode.varInt(is))
                .signature(Decode.varBytes(is))
                .build();
    }

    @Override
    public void write(OutputStream out) throws IOException {
        writeBytesToSign(out);
        Encode.varInt(signature.length, out);
        out.write(signature);
    }

    @Override
    public long getVersion() {
        return 3;
    }

    public long getNonceTrialsPerByte() {
        return nonceTrialsPerByte;
    }

    public long getExtraBytes() {
        return extraBytes;
    }

    public boolean isSigned() {
        return true;
    }

    public void writeBytesToSign(OutputStream out) throws IOException {
        super.write(out);
        Encode.varInt(nonceTrialsPerByte, out);
        Encode.varInt(extraBytes, out);
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        V3Pubkey pubkey = (V3Pubkey) o;
        return Objects.equals(nonceTrialsPerByte, pubkey.nonceTrialsPerByte) &&
                Objects.equals(extraBytes, pubkey.extraBytes) &&
                stream == pubkey.stream &&
                behaviorBitfield == pubkey.behaviorBitfield &&
                Arrays.equals(publicSigningKey, pubkey.publicSigningKey) &&
                Arrays.equals(publicEncryptionKey, pubkey.publicEncryptionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nonceTrialsPerByte, extraBytes);
    }

    public static class Builder {
        private long streamNumber;
        private int behaviorBitfield;
        private byte[] publicSigningKey;
        private byte[] publicEncryptionKey;
        private long nonceTrialsPerByte;
        private long extraBytes;
        private byte[] signature = new byte[0];

        public Builder stream(long streamNumber) {
            this.streamNumber = streamNumber;
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

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public V3Pubkey build() {
            return new V3Pubkey(3, this);
        }
    }
}
