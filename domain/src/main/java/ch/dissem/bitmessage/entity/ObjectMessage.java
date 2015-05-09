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

import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Encode;
import ch.dissem.bitmessage.utils.Security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The 'object' command sends an object that is shared throughout the network.
 */
public class ObjectMessage implements MessagePayload {
    private byte[] nonce;
    private long expiresTime;
    private long objectType;
    /**
     * The object's version
     */
    private long version;
    private long stream;

    private ObjectPayload payload;
    private byte[] payloadBytes;

    private ObjectMessage(Builder builder) {
        nonce = builder.nonce;
        expiresTime = builder.expiresTime;
        objectType = builder.objectType;
        version = builder.version;
        stream = builder.streamNumber;
        payload = builder.payload;
    }

    @Override
    public Command getCommand() {
        return Command.OBJECT;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public long getExpiresTime() {
        return expiresTime;
    }

    public long getType() {
        return objectType;
    }

    public ObjectPayload getPayload() {
        return payload;
    }

    public long getVersion() {
        return version;
    }

    public long getStream() {
        return stream;
    }

    public InventoryVector getInventoryVector() throws IOException {
        return new InventoryVector(Bytes.truncate(Security.doubleSha512(nonce, getPayloadBytesWithoutNonce()), 32));
    }

    public boolean isSigned() {
        return payload.isSigned();
    }

    private byte[] getBytesToSign() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeaderWithoutNonce(out);
        payload.writeBytesToSign(out);
        return out.toByteArray();
    }

    public void sign(PrivateKey key) {
        // TODO
    }

    public boolean isSignatureValid() throws IOException {
        Pubkey pubkey = null; // TODO
        return Security.isSignatureValid(getBytesToSign(), payload.getSignature(), pubkey);
    }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(nonce);
        out.write(getPayloadBytesWithoutNonce());
    }

    private void writeHeaderWithoutNonce(OutputStream out) throws IOException {
        Encode.int64(expiresTime, out);
        Encode.int32(objectType, out);
        Encode.varInt(version, out);
        Encode.varInt(stream, out);
    }

    public byte[] getPayloadBytesWithoutNonce() throws IOException {
        if (payloadBytes == null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeHeaderWithoutNonce(out);
            payload.write(out);
            payloadBytes = out.toByteArray();
        }
        return payloadBytes;
    }

    public static final class Builder {
        private byte[] nonce;
        private long expiresTime;
        private long objectType = -1;
        private long version = -1;
        private long streamNumber;
        private ObjectPayload payload;

        public Builder() {
        }

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder expiresTime(long expiresTime) {
            this.expiresTime = expiresTime;
            return this;
        }

        public Builder objectType(long objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder stream(long streamNumber) {
            this.streamNumber = streamNumber;
            return this;
        }

        public Builder payload(ObjectPayload payload) {
            this.payload = payload;
            if (this.objectType == -1)
                this.objectType = payload.getType().getNumber();
            return this;
        }

        public ObjectMessage build() {
            return new ObjectMessage(this);
        }
    }
}
