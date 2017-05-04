package ch.dissem.bitmessage.entity.valueobject.extended;

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.msgpack.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.*;

import static ch.dissem.bitmessage.entity.valueobject.extended.Attachment.Disposition.attachment;
import static ch.dissem.bitmessage.utils.Strings.str;
import static ch.dissem.msgpack.types.Utils.mp;

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

    @Override
    public MPMap<MPString, MPType<?>> pack() throws IOException {
        MPMap<MPString, MPType<?>> result = new MPMap<>();
        result.put(mp(""), mp(TYPE));
        result.put(mp("subject"), mp(subject));
        result.put(mp("body"), mp(body));

        if (!files.isEmpty()) {
            MPArray<MPMap<MPString, MPType<?>>> items = new MPArray<>();
            result.put(mp("files"), items);
            for (Attachment file : files) {
                MPMap<MPString, MPType<?>> item = new MPMap<>();
                item.put(mp("name"), mp(file.getName()));
                item.put(mp("data"), mp(file.getData()));
                item.put(mp("type"), mp(file.getType()));
                item.put(mp("disposition"), mp(file.getDisposition().name()));
                items.add(item);
            }
        }
        if (!parents.isEmpty()) {
            MPArray<MPBinary> items = new MPArray<>();
            result.put(mp("parents"), items);
            for (InventoryVector parent : parents) {
                items.add(mp(parent.getHash()));
            }
        }
        return result;
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
            if (parent != null) {
                InventoryVector iv = parent.getInventoryVector();
                if (iv == null) {
                    LOG.debug("Ignored parent without IV");
                } else {
                    parents.add(iv);
                }
            }
            return this;
        }

        public Builder addParent(InventoryVector iv) {
            if (iv != null) {
                parents.add(iv);
            }
            return this;
        }

        public Builder addFile(File file, Attachment.Disposition disposition) {
            if (file != null) {
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
            }
            return this;
        }

        public Builder addFile(Attachment file) {
            if (file != null) {
                files.add(file);
            }
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
        public Message unpack(MPMap<MPString, MPType<?>> map) {
            Message.Builder builder = new Message.Builder();
            builder.subject(str(map.get(mp("subject"))));
            builder.body(str(map.get(mp("body"))));
            @SuppressWarnings("unchecked")
            MPArray<MPBinary> parents = (MPArray<MPBinary>) map.get(mp("parents"));
            if (parents != null) {
                for (MPBinary parent : parents) {
                    builder.addParent(new InventoryVector(parent.getValue()));
                }
            }
            @SuppressWarnings("unchecked")
            MPArray<MPMap<MPString, MPType<?>>> files = (MPArray<MPMap<MPString, MPType<?>>>) map.get(mp("files"));
            if (files != null) {
                for (MPMap<MPString, MPType<?>> item : files) {
                    Attachment.Builder b = new Attachment.Builder();
                    b.name(str(item.get(mp("name"))));
                    b.data(bin(item.get(mp("data"))));
                    b.type(str(item.get(mp("type"))));
                    String disposition = str(item.get(mp("disposition")));
                    if ("inline".equals(disposition)) {
                        b.inline();
                    } else if ("attachment".equals(disposition)) {
                        b.attachment();
                    }
                    builder.addFile(b.build());
                }
            }

            return new Message(builder);
        }

        private byte[] bin(MPType data) {
            if (data instanceof MPBinary) {
                return ((MPBinary) data).getValue();
            } else {
                return null;
            }
        }
    }
}
