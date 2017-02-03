package ch.dissem.bitmessage.entity.valueobject.extended;

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.msgpack.types.*;

import java.io.IOException;
import java.util.Objects;

import static ch.dissem.bitmessage.utils.Strings.str;
import static ch.dissem.msgpack.types.Utils.mp;

/**
 * Extended encoding type 'vote'. Specification still outstanding, so this will need some work.
 */
public class Vote implements ExtendedEncoding.ExtendedType {
    private static final long serialVersionUID = -8427038604209964837L;

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

    @Override
    public MPMap<MPString, MPType<?>> pack() throws IOException {
        MPMap<MPString, MPType<?>> result = new MPMap<>();
        result.put(mp(""), mp(TYPE));
        result.put(mp("msgId"), mp(msgId.getHash()));
        result.put(mp("vote"), mp(vote));
        return result;
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
        public Vote unpack(MPMap<MPString, MPType<?>> map) {
            Vote.Builder builder = new Vote.Builder();
            MPType<?> msgId = map.get(mp("msgId"));
            if (msgId instanceof MPBinary) {
                builder.msgId(new InventoryVector(((MPBinary) msgId).getValue()));
            }
            builder.vote(str(map.get(mp("vote"))));
            return new Vote(builder);
        }
    }
}
