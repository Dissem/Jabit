package ch.dissem.bitmessage.entity.valueobject.extended;

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Extended encoding type 'vote'. Specification still outstanding, so this will need some work.
 */
public class Vote implements ExtendedEncoding.ExtendedType {
    private static final long serialVersionUID = -8427038604209964837L;
    private static final Logger LOG = LoggerFactory.getLogger(Vote.class);

    public static final String TYPE = "vote";

    private InventoryVector msgId;
    private String vote;

    private Vote(Builder builder) {
        msgId = builder.msgId;
        vote = builder.vote;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public InventoryVector getMsgId() {
        return msgId;
    }

    public String getVote() {
        return vote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote1 = (Vote) o;
        return Objects.equals(msgId, vote1.msgId) &&
            Objects.equals(vote, vote1.vote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgId, vote);
    }

    public void pack(MessagePacker packer) throws IOException {
        packer.packMapHeader(3);
        packer.packString("");
        packer.packString(TYPE);
        packer.packString("msgId");
        packer.packBinaryHeader(msgId.getHash().length);
        packer.writePayload(msgId.getHash());
        packer.packString("vote");
        packer.packString(vote);
    }

    public static class Builder {
        private InventoryVector msgId;
        private String vote;

        public ExtendedEncoding up(Plaintext message) {
            msgId = message.getInventoryVector();
            vote = "1";
            return new ExtendedEncoding(new Vote(this));
        }

        public ExtendedEncoding down(Plaintext message) {
            msgId = message.getInventoryVector();
            vote = "1";
            return new ExtendedEncoding(new Vote(this));
        }

        public Builder msgId(InventoryVector iv) {
            this.msgId = iv;
            return this;
        }

        public Builder vote(String vote) {
            this.vote = vote;
            return this;
        }

        public ExtendedEncoding build() {
            return new ExtendedEncoding(new Vote(this));
        }
    }

    public static class Unpacker implements ExtendedEncoding.Unpacker<Vote> {
        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public Vote unpack(MessageUnpacker unpacker, int size) {
            Vote.Builder builder = new Vote.Builder();
            try {
                for (int i = 0; i < size; i++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "msgId":
                            int binarySize = unpacker.unpackBinaryHeader();
                            builder.msgId(new InventoryVector(unpacker.readPayload(binarySize)));
                            break;
                        case "vote":
                            builder.vote(unpacker.unpackString());
                            break;
                        default:
                            LOG.error("Unexpected data with key: " + key);
                            break;
                    }
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
            return new Vote(builder);
        }
    }
}
