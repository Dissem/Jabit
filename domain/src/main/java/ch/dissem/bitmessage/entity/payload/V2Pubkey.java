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

/**
 * A version 2 public key.
 */
public class V2Pubkey extends Pubkey {
    protected long stream;
    protected int behaviorBitfield;
    protected byte[] publicSigningKey; // 64 Bytes
    protected byte[] publicEncryptionKey; // 64 Bytes

    protected V2Pubkey() {
    }

    private V2Pubkey(Builder builder) {
        stream = builder.streamNumber;
        behaviorBitfield = builder.behaviorBitfield;
        publicSigningKey = add0x04(builder.publicSigningKey);
        publicEncryptionKey = add0x04(builder.publicEncryptionKey);
    }

    public static V2Pubkey read(InputStream is, long stream) throws IOException {
        return new V2Pubkey.Builder()
                .stream(stream)
                .behaviorBitfield((int) Decode.uint32(is))
                .publicSigningKey(Decode.bytes(is, 64))
                .publicEncryptionKey(Decode.bytes(is, 64))
                .build();
    }

    @Override
    public long getVersion() {
        return 2;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.PUBKEY;
    }

    @Override
    public long getStream() {
        return stream;
    }

    @Override
    public byte[] getSigningKey() {
        return publicSigningKey;
    }

    @Override
    public byte[] getEncryptionKey() {
        return publicEncryptionKey;
    }

    @Override
    public int getBehaviorBitfield() {
        return behaviorBitfield;
    }

    @Override
    public void write(OutputStream os) throws IOException {
        Encode.int32(behaviorBitfield, os);
        os.write(publicSigningKey, 1, 64);
        os.write(publicEncryptionKey, 1, 64);
    }

    public static class Builder {
        private long streamNumber;
        private int behaviorBitfield;
        private byte[] publicSigningKey;
        private byte[] publicEncryptionKey;

        public Builder() {
        }

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

        public V2Pubkey build() {
            return new V2Pubkey(this);
        }
    }
}
