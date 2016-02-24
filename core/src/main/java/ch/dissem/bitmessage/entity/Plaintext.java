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

import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.Decode;
import ch.dissem.bitmessage.utils.Encode;
import ch.dissem.bitmessage.utils.UnixTime;

import java.io.*;
import java.util.*;

/**
 * The unencrypted message to be sent by 'msg' or 'broadcast'.
 */
public class Plaintext implements Streamable {
    private final Type type;
    private final BitmessageAddress from;
    private final long encoding;
    private final byte[] message;
    private final byte[] ack;
    private Object id;
    private InventoryVector inventoryVector;
    private BitmessageAddress to;
    private byte[] signature;
    private Status status;
    private Long sent;
    private Long received;

    private Set<Label> labels;
    private byte[] initialHash;

    private Plaintext(Builder builder) {
        id = builder.id;
        inventoryVector = builder.inventoryVector;
        type = builder.type;
        from = builder.from;
        to = builder.to;
        encoding = builder.encoding;
        message = builder.message;
        ack = builder.ack;
        signature = builder.signature;
        status = builder.status;
        sent = builder.sent;
        received = builder.received;
        labels = builder.labels;
    }

    public static Plaintext read(Type type, InputStream in) throws IOException {
        return readWithoutSignature(type, in)
                .signature(Decode.varBytes(in))
                .received(UnixTime.now())
                .build();
    }

    public static Plaintext.Builder readWithoutSignature(Type type, InputStream in) throws IOException {
        long version = Decode.varInt(in);
        return new Builder(type)
                .addressVersion(version)
                .stream(Decode.varInt(in))
                .behaviorBitfield(Decode.int32(in))
                .publicSigningKey(Decode.bytes(in, 64))
                .publicEncryptionKey(Decode.bytes(in, 64))
                .nonceTrialsPerByte(version >= 3 ? Decode.varInt(in) : 0)
                .extraBytes(version >= 3 ? Decode.varInt(in) : 0)
                .destinationRipe(type == Type.MSG ? Decode.bytes(in, 20) : null)
                .encoding(Decode.varInt(in))
                .message(Decode.varBytes(in))
                .ack(type == Type.MSG ? Decode.varBytes(in) : null);
    }

    public InventoryVector getInventoryVector() {
        return inventoryVector;
    }

    public void setInventoryVector(InventoryVector inventoryVector) {
        this.inventoryVector = inventoryVector;
    }

    public Type getType() {
        return type;
    }

    public byte[] getMessage() {
        return message;
    }

    public BitmessageAddress getFrom() {
        return from;
    }

    public BitmessageAddress getTo() {
        return to;
    }

    public void setTo(BitmessageAddress to) {
        if (this.to.getVersion() != 0)
            throw new IllegalStateException("Correct address already set");
        if (!Arrays.equals(this.to.getRipe(), to.getRipe())) {
            throw new IllegalArgumentException("RIPEs don't match");
        }
        this.to = to;
    }

    public Set<Label> getLabels() {
        return labels;
    }

    public long getStream() {
        return from.getStream();
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean isUnread() {
        for (Label label : labels) {
            if (label.getType() == Label.Type.UNREAD) {
                return true;
            }
        }
        return false;
    }

    public void write(OutputStream out, boolean includeSignature) throws IOException {
        Encode.varInt(from.getVersion(), out);
        Encode.varInt(from.getStream(), out);
        Encode.int32(from.getPubkey().getBehaviorBitfield(), out);
        out.write(from.getPubkey().getSigningKey(), 1, 64);
        out.write(from.getPubkey().getEncryptionKey(), 1, 64);
        if (from.getVersion() >= 3) {
            Encode.varInt(from.getPubkey().getNonceTrialsPerByte(), out);
            Encode.varInt(from.getPubkey().getExtraBytes(), out);
        }
        if (type == Type.MSG) {
            out.write(to.getRipe());
        }
        Encode.varInt(encoding, out);
        Encode.varInt(message.length, out);
        out.write(message);
        if (type == Type.MSG) {
            Encode.varInt(ack.length, out);
            out.write(ack);
        }
        if (includeSignature) {
            if (signature == null) {
                Encode.varInt(0, out);
            } else {
                Encode.varInt(signature.length, out);
                out.write(signature);
            }
        }
    }

    @Override
    public void write(OutputStream out) throws IOException {
        write(out, true);
    }

    public Object getId() {
        return id;
    }

    public void setId(long id) {
        if (this.id != null) throw new IllegalStateException("ID already set");
        this.id = id;
    }

    public Long getSent() {
        return sent;
    }

    public Long getReceived() {
        return received;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getSubject() {
        Scanner s = new Scanner(new ByteArrayInputStream(message), "UTF-8");
        String firstLine = s.nextLine();
        if (encoding == 2) {
            return firstLine.substring("Subject:".length()).trim();
        } else if (firstLine.length() > 50) {
            return firstLine.substring(0, 50).trim() + "...";
        } else {
            return firstLine;
        }
    }

    public String getText() {
        try {
            String text = new String(message, "UTF-8");
            if (encoding == 2) {
                return text.substring(text.indexOf("\nBody:") + 6);
            }
            return text;
        } catch (UnsupportedEncodingException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Plaintext plaintext = (Plaintext) o;
        return Objects.equals(encoding, plaintext.encoding) &&
                Objects.equals(from, plaintext.from) &&
                Arrays.equals(message, plaintext.message) &&
                Arrays.equals(ack, plaintext.ack) &&
                Arrays.equals(to.getRipe(), plaintext.to.getRipe()) &&
                Arrays.equals(signature, plaintext.signature) &&
                Objects.equals(status, plaintext.status) &&
                Objects.equals(sent, plaintext.sent) &&
                Objects.equals(received, plaintext.received) &&
                Objects.equals(labels, plaintext.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, encoding, message, ack, to, signature, status, sent, received, labels);
    }

    public void addLabels(Label... labels) {
        if (labels != null) {
            Collections.addAll(this.labels, labels);
        }
    }

    public void addLabels(Collection<Label> labels) {
        if (labels != null) {
            this.labels.addAll(labels);
        }
    }

    public void setInitialHash(byte[] initialHash) {
        this.initialHash = initialHash;
    }

    public byte[] getInitialHash() {
        return initialHash;
    }

    public enum Encoding {
        IGNORE(0), TRIVIAL(1), SIMPLE(2);

        long code;

        Encoding(long code) {
            this.code = code;
        }

        public long getCode() {
            return code;
        }
    }

    public enum Status {
        DRAFT,
        // For sent messages
        PUBKEY_REQUESTED,
        DOING_PROOF_OF_WORK,
        SENT,
        SENT_ACKNOWLEDGED,
        RECEIVED
    }

    public enum Type {
        MSG, BROADCAST
    }

    public static final class Builder {
        private Object id;
        private InventoryVector inventoryVector;
        private Type type;
        private BitmessageAddress from;
        private BitmessageAddress to;
        private long addressVersion;
        private long stream;
        private int behaviorBitfield;
        private byte[] publicSigningKey;
        private byte[] publicEncryptionKey;
        private long nonceTrialsPerByte;
        private long extraBytes;
        private byte[] destinationRipe;
        private long encoding;
        private byte[] message = new byte[0];
        private byte[] ack = new byte[0];
        private byte[] signature;
        private long sent;
        private long received;
        private Status status;
        private Set<Label> labels = new HashSet<>();

        public Builder(Type type) {
            this.type = type;
        }

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public Builder IV(InventoryVector iv) {
            this.inventoryVector = iv;
            return this;
        }

        public Builder from(BitmessageAddress address) {
            from = address;
            return this;
        }

        public Builder to(BitmessageAddress address) {
            if (type != Type.MSG && to != null)
                throw new IllegalArgumentException("recipient address only allowed for msg");
            to = address;
            return this;
        }

        private Builder addressVersion(long addressVersion) {
            this.addressVersion = addressVersion;
            return this;
        }

        private Builder stream(long stream) {
            this.stream = stream;
            return this;
        }

        private Builder behaviorBitfield(int behaviorBitfield) {
            this.behaviorBitfield = behaviorBitfield;
            return this;
        }

        private Builder publicSigningKey(byte[] publicSigningKey) {
            this.publicSigningKey = publicSigningKey;
            return this;
        }

        private Builder publicEncryptionKey(byte[] publicEncryptionKey) {
            this.publicEncryptionKey = publicEncryptionKey;
            return this;
        }

        private Builder nonceTrialsPerByte(long nonceTrialsPerByte) {
            this.nonceTrialsPerByte = nonceTrialsPerByte;
            return this;
        }

        private Builder extraBytes(long extraBytes) {
            this.extraBytes = extraBytes;
            return this;
        }

        private Builder destinationRipe(byte[] ripe) {
            if (type != Type.MSG && ripe != null) throw new IllegalArgumentException("ripe only allowed for msg");
            this.destinationRipe = ripe;
            return this;
        }

        public Builder encoding(Encoding encoding) {
            this.encoding = encoding.getCode();
            return this;
        }

        private Builder encoding(long encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder message(String subject, String message) {
            try {
                this.encoding = Encoding.SIMPLE.getCode();
                this.message = ("Subject:" + subject + '\n' + "Body:" + message).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new ApplicationException(e);
            }
            return this;
        }

        public Builder message(byte[] message) {
            this.message = message;
            return this;
        }

        public Builder ack(byte[] ack) {
            if (type != Type.MSG && ack != null) throw new IllegalArgumentException("ack only allowed for msg");
            this.ack = ack;
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public Builder sent(long sent) {
            this.sent = sent;
            return this;
        }

        public Builder received(long received) {
            this.received = received;
            return this;
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder labels(Collection<Label> labels) {
            this.labels.addAll(labels);
            return this;
        }

        public Plaintext build() {
            if (from == null) {
                from = new BitmessageAddress(Factory.createPubkey(
                        addressVersion,
                        stream,
                        publicSigningKey,
                        publicEncryptionKey,
                        nonceTrialsPerByte,
                        extraBytes,
                        behaviorBitfield
                ));
            }
            if (to == null && type != Type.BROADCAST && destinationRipe != null) {
                to = new BitmessageAddress(0, 0, destinationRipe);
            }
            return new Plaintext(this);
        }
    }
}
