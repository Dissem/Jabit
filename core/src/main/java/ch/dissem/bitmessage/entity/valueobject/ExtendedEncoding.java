package ch.dissem.bitmessage.entity.valueobject;

import ch.dissem.bitmessage.exception.ApplicationException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Extended encoding message object.
 */
public class ExtendedEncoding implements Serializable {
    private static final long serialVersionUID = 3876871488247305200L;

    private Message message;

    public ExtendedEncoding(Message message) {
        this.message = message;
    }

    private ExtendedEncoding() {
    }

    public Message getMessage() {
        return message;
    }

    public byte[] zip() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DeflaterOutputStream zipper = new DeflaterOutputStream(out)) {

            MessagePacker packer = MessagePack.newDefaultPacker(zipper);
            // FIXME: this should work for trivial cases
            if (message != null) {
                message.pack(packer);
            }
            packer.close();
            zipper.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static ExtendedEncoding unzip(byte[] zippedData) {
        ExtendedEncoding result = new ExtendedEncoding();
        try (InflaterInputStream unzipper = new InflaterInputStream(new ByteArrayInputStream(zippedData))) {
            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(unzipper);
            int mapSize = unpacker.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "":
                        switch (unpacker.unpackString()) {
                            case "message":
                                result.message = new Message();
                                break;
                        }
                        break;
                    case "subject":
                        result.message.subject = unpacker.unpackString();
                        break;
                    case "body":
                        result.message.body = unpacker.unpackString();
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtendedEncoding that = (ExtendedEncoding) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    public static class Message implements Serializable {
        private static final long serialVersionUID = -2724977231484285467L;

        private String subject;
        private String body;
        private List<InventoryVector> parents;
        private List<Attachment> files;

        private Message() {
            parents = Collections.emptyList();
            files = Collections.emptyList();
        }

        private Message(Builder builder) {
            subject = builder.subject;
            body = builder.body;
            parents = Collections.unmodifiableList(builder.parents);
            files = Collections.unmodifiableList(builder.files);
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }

        public List<InventoryVector> getParents() {
            return parents;
        }

        public List<Attachment> getFiles() {
            return files;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Message message = (Message) o;
            return Objects.equals(subject, message.subject) &&
                Objects.equals(body, message.body) &&
                Objects.equals(parents, message.parents) &&
                Objects.equals(files, message.files);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, body, parents, files);
        }

        public void pack(MessagePacker packer) throws IOException {
            packer.packMapHeader(3);
            packer.packString("");
            packer.packString("message");
            packer.packString("subject");
            packer.packString(subject);
            packer.packString("body");
            packer.packString(body);
        }

        public static class Builder {
            private String subject;
            private String body;
            private List<InventoryVector> parents = new LinkedList<>();
            private List<Attachment> files = new LinkedList<>();

            private Builder() {
            }

            public Builder subject(String subject) {
                this.subject = subject;
                return this;
            }

            public Builder body(String body) {
                this.body = body;
                return this;
            }

            public Builder addParent(InventoryVector iv) {
                parents.add(iv);
                return this;
            }

            public Builder addFile(Attachment file) {
                files.add(file);
                return this;
            }

            public ExtendedEncoding build() {
                return new ExtendedEncoding(new Message(this));
            }
        }
    }

    public static class Builder {
        public Message.Builder message() {
            return new Message.Builder();
        }

        // TODO: vote (etc.?)
    }
}
