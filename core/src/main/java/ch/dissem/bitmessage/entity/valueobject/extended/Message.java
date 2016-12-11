package ch.dissem.bitmessage.entity.valueobject.extended;

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.*;

/**
 * Extended encoding type 'message'. Properties 'parents' and 'files' not yet supported by PyBitmessage, so they might not work
 * properly with future PyBitmessage implementations.
 */
public class Message implements ExtendedEncoding.ExtendedType {
    private static final long serialVersionUID = -2724977231484285467L;
    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    public static final String TYPE = "message";

    private String subject;
    private String body;
    private List<InventoryVector> parents;
    private List<Attachment> files;

    private Message(Builder builder) {
        subject = builder.subject;
        body = builder.body;
        parents = Collections.unmodifiableList(builder.parents);
        files = Collections.unmodifiableList(builder.files);
    }

    @Override
    public String getType() {
        return TYPE;
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
        int size = 3;
        if (!files.isEmpty()) {
            size++;
        }
        if (!parents.isEmpty()) {
            size++;
        }
        packer.packMapHeader(size);
        packer.packString("");
        packer.packString("message");
        packer.packString("subject");
        packer.packString(subject);
        packer.packString("body");
        packer.packString(body);
        if (!files.isEmpty()) {
            packer.packString("files");
            packer.packArrayHeader(files.size());
            for (Attachment file : files) {
                packer.packMapHeader(4);
                packer.packString("name");
                packer.packString(file.getName());
                packer.packString("data");
                packer.packBinaryHeader(file.getData().length);
                packer.writePayload(file.getData());
                packer.packString("type");
                packer.packString(file.getType());
                packer.packString("disposition");
                packer.packString(file.getDisposition().name());
            }
        }
        if (!parents.isEmpty()) {
            packer.packString("parents");
            packer.packArrayHeader(parents.size());
            for (InventoryVector parent : parents) {
                packer.packBinaryHeader(parent.getHash().length);
                packer.writePayload(parent.getHash());
            }
        }
    }

    public static class Builder {
        private String subject;
        private String body;
        private List<InventoryVector> parents = new LinkedList<>();
        private List<Attachment> files = new LinkedList<>();

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder addParent(Plaintext parent) {
            parents.add(parent.getInventoryVector());
            return this;
        }

        public Builder addParent(InventoryVector iv) {
            parents.add(iv);
            return this;
        }

        public Builder addFile(File file, Attachment.Disposition disposition) {
            try {
                files.add(new Attachment.Builder()
                    .name(file.getName())
                    .disposition(disposition)
                    .type(URLConnection.guessContentTypeFromStream(new FileInputStream(file)))
                    .data(Files.readAllBytes(file.toPath()))
                    .build());
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
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

    public static class Unpacker implements ExtendedEncoding.Unpacker<Message> {
        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public Message unpack(MessageUnpacker unpacker, int size) {
            Message.Builder builder = new Message.Builder();
            try {
                for (int i = 0; i < size; i++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "subject":
                            builder.subject(unpacker.unpackString());
                            break;
                        case "body":
                            builder.body(unpacker.unpackString());
                            break;
                        case "parents":
                            builder.parents = unpackParents(unpacker);
                            break;
                        case "files":
                            builder.files = unpackFiles(unpacker);
                            break;
                        default:
                            LOG.error("Unexpected data with key: " + key);
                            break;
                    }
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
            return new Message(builder);
        }

        private static List<InventoryVector> unpackParents(MessageUnpacker unpacker) throws IOException {
            int size = unpacker.unpackArrayHeader();
            List<InventoryVector> parents = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int binarySize = unpacker.unpackBinaryHeader();
                parents.add(new InventoryVector(unpacker.readPayload(binarySize)));
            }
            return parents;
        }

        private static List<Attachment> unpackFiles(MessageUnpacker unpacker) throws IOException {
            int size = unpacker.unpackArrayHeader();
            List<Attachment> files = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Attachment.Builder attachment = new Attachment.Builder();
                int mapSize = unpacker.unpackMapHeader();
                for (int j = 0; j < mapSize; j++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "name":
                            attachment.name(unpacker.unpackString());
                            break;
                        case "data":
                            int binarySize = unpacker.unpackBinaryHeader();
                            attachment.data(unpacker.readPayload(binarySize));
                            break;
                        case "type":
                            attachment.type(unpacker.unpackString());
                            break;
                        case "disposition":
                            String disposition = unpacker.unpackString();
                            switch (disposition) {
                                case "inline":
                                    attachment.inline();
                                    break;
                                case "attachment":
                                    attachment.attachment();
                                    break;
                                default:
                                    LOG.debug("Unknown disposition: " + disposition);
                                    break;
                            }
                            break;
                        default:
                            LOG.debug("Unknown file info '" + key + "' with data: " + unpacker.unpackValue());
                            break;
                    }
                }
                files.add(attachment.build());
            }
            return files;
        }
    }
}
