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

import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.entity.valueobject.extended.Attachment;
import ch.dissem.bitmessage.entity.valueobject.extended.Message;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.factory.ExtendedEncodingFactory;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Collections;

import static ch.dissem.bitmessage.entity.Plaintext.Encoding.EXTENDED;
import static ch.dissem.bitmessage.entity.Plaintext.Encoding.SIMPLE;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * The unencrypted message to be sent by 'msg' or 'broadcast'.
 */
public class Plaintext implements Streamable {
    private static final long serialVersionUID = -5325729856394951079L;

    private final Type type;
    private final BitmessageAddress from;
    private final long encoding;
    private final byte[] message;
    private final byte[] ackData;
    private final UUID conversationId;
    private ExtendedEncoding extendedData;
    private ObjectMessage ackMessage;
    private Object id;
    private InventoryVector inventoryVector;
    private BitmessageAddress to;
    private byte[] signature;
    private Status status;
    private Long sent;
    private Long received;

    private Set<Label> labels;
    private byte[] initialHash;

    private long ttl;
    private int retries;
    private Long nextTry;

    private Plaintext(Builder builder) {
        id = builder.id;
        inventoryVector = builder.inventoryVector;
        type = builder.type;
        from = builder.from;
        to = builder.to;
        encoding = builder.encoding;
        message = builder.message;
        ackData = builder.ackData;
        if (builder.ackMessage != null && builder.ackMessage.length > 0) {
            ackMessage = Factory.getObjectMessage(
                3,
                new ByteArrayInputStream(builder.ackMessage),
                builder.ackMessage.length);
        }
        signature = builder.signature;
        status = builder.status;
        sent = builder.sent;
        received = builder.received;
        labels = builder.labels;
        ttl = builder.ttl;
        retries = builder.retries;
        nextTry = builder.nextTry;
        conversationId = builder.conversation;
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
            .ackMessage(type == Type.MSG ? Decode.varBytes(in) : null);
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

    public Encoding getEncoding() {
        return Encoding.fromCode(encoding);
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
        if (from.getPubkey() == null) {
            Encode.int32(0, out);
            byte[] empty = new byte[64];
            out.write(empty);
            out.write(empty);
            if (from.getVersion() >= 3) {
                Encode.varInt(0, out);
                Encode.varInt(0, out);
            }
        } else {
            Encode.int32(from.getPubkey().getBehaviorBitfield(), out);
            out.write(from.getPubkey().getSigningKey(), 1, 64);
            out.write(from.getPubkey().getEncryptionKey(), 1, 64);
            if (from.getVersion() >= 3) {
                Encode.varInt(from.getPubkey().getNonceTrialsPerByte(), out);
                Encode.varInt(from.getPubkey().getExtraBytes(), out);
            }
        }
        if (type == Type.MSG) {
            out.write(to.getRipe());
        }
        Encode.varInt(encoding, out);
        Encode.varInt(message.length, out);
        out.write(message);
        if (type == Type.MSG) {
            if (to.has(Feature.DOES_ACK) && getAckMessage() != null) {
                ByteArrayOutputStream ack = new ByteArrayOutputStream();
                getAckMessage().write(ack);
                Encode.varBytes(ack.toByteArray(), out);
            } else {
                Encode.varInt(0, out);
            }
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

    public void write(ByteBuffer buffer, boolean includeSignature) {
        Encode.varInt(from.getVersion(), buffer);
        Encode.varInt(from.getStream(), buffer);
        if (from.getPubkey() == null) {
            Encode.int32(0, buffer);
            byte[] empty = new byte[64];
            buffer.put(empty);
            buffer.put(empty);
            if (from.getVersion() >= 3) {
                Encode.varInt(0, buffer);
                Encode.varInt(0, buffer);
            }
        } else {
            Encode.int32(from.getPubkey().getBehaviorBitfield(), buffer);
            buffer.put(from.getPubkey().getSigningKey(), 1, 64);
            buffer.put(from.getPubkey().getEncryptionKey(), 1, 64);
            if (from.getVersion() >= 3) {
                Encode.varInt(from.getPubkey().getNonceTrialsPerByte(), buffer);
                Encode.varInt(from.getPubkey().getExtraBytes(), buffer);
            }
        }
        if (type == Type.MSG) {
            buffer.put(to.getRipe());
        }
        Encode.varInt(encoding, buffer);
        Encode.varInt(message.length, buffer);
        buffer.put(message);
        if (type == Type.MSG) {
            if (to.has(Feature.DOES_ACK) && getAckMessage() != null) {
                Encode.varBytes(Encode.bytes(getAckMessage()), buffer);
            } else {
                Encode.varInt(0, buffer);
            }
        }
        if (includeSignature) {
            if (signature == null) {
                Encode.varInt(0, buffer);
            } else {
                Encode.varInt(signature.length, buffer);
                buffer.put(signature);
            }
        }
    }

    @Override
    public void write(OutputStream out) throws IOException {
        write(out, true);
    }

    @Override
    public void write(ByteBuffer buffer) {
        write(buffer, true);
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
        if (status != Status.RECEIVED && sent == null && status != Status.DRAFT) {
            sent = UnixTime.now();
        }
        this.status = status;
    }

    public long getTTL() {
        return ttl;
    }

    public int getRetries() {
        return retries;
    }

    public Long getNextTry() {
        return nextTry;
    }

    public void updateNextTry() {
        if (to != null) {
            if (nextTry == null) {
                if (sent != null && to.has(Feature.DOES_ACK)) {
                    nextTry = UnixTime.now(+ttl);
                    retries++;
                }
            } else {
                nextTry = nextTry + (1 << retries) * ttl;
                retries++;
            }
        }
    }

    public String getSubject() {
        Scanner s = new Scanner(new ByteArrayInputStream(message), "UTF-8");
        String firstLine = s.nextLine();
        if (encoding == EXTENDED.code) {
            if (Message.TYPE.equals(getExtendedData().getType())) {
                return ((Message) extendedData.getContent()).getSubject();
            } else {
                return null;
            }
        } else if (encoding == SIMPLE.code) {
            return firstLine.substring("Subject:".length()).trim();
        } else if (firstLine.length() > 50) {
            return firstLine.substring(0, 50).trim() + "...";
        } else {
            return firstLine;
        }
    }

    public String getText() {
        if (encoding == EXTENDED.code) {
            if (Message.TYPE.equals(getExtendedData().getType())) {
                return ((Message) extendedData.getContent()).getBody();
            } else {
                return null;
            }
        } else {
            try {
                String text = new String(message, "UTF-8");
                if (encoding == SIMPLE.code) {
                    return text.substring(text.indexOf("\nBody:") + 6);
                }
                return text;
            } catch (UnsupportedEncodingException e) {
                throw new ApplicationException(e);
            }
        }
    }

    protected ExtendedEncoding getExtendedData() {
        if (extendedData == null && encoding == EXTENDED.code) {
            // TODO: make sure errors are properly handled
            extendedData = ExtendedEncodingFactory.getInstance().unzip(message);
        }
        return extendedData;
    }

    @SuppressWarnings("unchecked")
    public <T extends ExtendedEncoding.ExtendedType> T getExtendedData(Class<T> type) {
        ExtendedEncoding extendedData = getExtendedData();
        if (extendedData == null) {
            return null;
        }
        if (type == null || type.isInstance(extendedData.getContent())) {
            return (T) extendedData.getContent();
        }
        return null;
    }

    public List<InventoryVector> getParents() {
        if (getExtendedData() != null && Message.TYPE.equals(getExtendedData().getType())) {
            return ((Message) extendedData.getContent()).getParents();
        } else {
            return Collections.emptyList();
        }
    }

    public List<Attachment> getFiles() {
        if (Message.TYPE.equals(getExtendedData().getType())) {
            return ((Message) extendedData.getContent()).getFiles();
        } else {
            return Collections.emptyList();
        }
    }

    public UUID getConversationId() {
        return conversationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Plaintext plaintext = (Plaintext) o;
        return Objects.equals(encoding, plaintext.encoding) &&
            Objects.equals(from, plaintext.from) &&
            Arrays.equals(message, plaintext.message) &&
            Objects.equals(getAckMessage(), plaintext.getAckMessage()) &&
            Arrays.equals(to == null ? null : to.getRipe(), plaintext.to == null ? null : plaintext.to.getRipe()) &&
            Arrays.equals(signature, plaintext.signature) &&
            Objects.equals(status, plaintext.status) &&
            Objects.equals(sent, plaintext.sent) &&
            Objects.equals(received, plaintext.received) &&
            Objects.equals(labels, plaintext.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, encoding, message, ackData, to, signature, status, sent, received, labels);
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

    public void removeLabel(Label.Type type) {
        Iterator<Label> iterator = labels.iterator();
        while (iterator.hasNext()) {
            Label label = iterator.next();
            if (label.getType() == type) {
                iterator.remove();
            }
        }
    }

    public byte[] getAckData() {
        return ackData;
    }

    public ObjectMessage getAckMessage() {
        if (ackMessage == null) {
            ackMessage = Factory.createAck(this);
        }
        return ackMessage;
    }

    public void setInitialHash(byte[] initialHash) {
        this.initialHash = initialHash;
    }

    public byte[] getInitialHash() {
        return initialHash;
    }

    @Override
    public String toString() {
        String subject = getSubject();
        if (subject == null || subject.length() == 0) {
            return Strings.hex(initialHash).toString();
        } else {
            return subject;
        }
    }

    public enum Encoding {
        IGNORE(0), TRIVIAL(1), SIMPLE(2), EXTENDED(3);

        long code;

        Encoding(long code) {
            this.code = code;
        }

        public long getCode() {
            return code;
        }

        public static Encoding fromCode(long code) {
            for (Encoding e : values()) {
                if (e.getCode() == code) {
                    return e;
                }
            }
            return null;
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
        private byte[] ackData;
        private byte[] ackMessage;
        private byte[] signature;
        private Long sent;
        private Long received;
        private Status status;
        private Set<Label> labels = new LinkedHashSet<>();
        private long ttl;
        private int retries;
        private Long nextTry;
        private UUID conversation;

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

        public Builder message(ExtendedEncoding message) {
            this.encoding = EXTENDED.getCode();
            this.message = message.zip();
            return this;
        }

        public Builder message(String subject, String message) {
            try {
                this.encoding = SIMPLE.getCode();
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

        public Builder ackMessage(byte[] ack) {
            if (type != Type.MSG && ack != null) throw new IllegalArgumentException("ackMessage only allowed for msg");
            this.ackMessage = ack;
            return this;
        }

        public Builder ackData(byte[] ackData) {
            if (type != Type.MSG && ackData != null)
                throw new IllegalArgumentException("ackMessage only allowed for msg");
            this.ackData = ackData;
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public Builder sent(Long sent) {
            this.sent = sent;
            return this;
        }

        public Builder received(Long received) {
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

        public Builder ttl(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder nextTry(Long nextTry) {
            this.nextTry = nextTry;
            return this;
        }

        public Builder conversation(UUID id) {
            this.conversation = id;
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
            if (type == Type.MSG && ackMessage == null && ackData == null) {
                ackData = cryptography().randomBytes(Msg.ACK_LENGTH);
            }
            if (ttl <= 0) {
                ttl = TTL.msg();
            }
            if (conversation == null) {
                conversation = UUID.randomUUID();
            }
            return new Plaintext(this);
        }
    }
}
