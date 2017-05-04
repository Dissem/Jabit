package ch.dissem.bitmessage.entity.valueobject.extended;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * A "file" attachment as used by extended encoding type messages. Could either be an attachment,
 * or used inline to be used by a HTML message, for example.
 */
public class Attachment implements Serializable {
    private static final long serialVersionUID = 7319139427666943189L;

    private String name;
    private byte[] data;
    private String type;
    private Disposition disposition;

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public String getType() {
        return type;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment that = (Attachment) o;
        return Objects.equals(name, that.name) &&
            Arrays.equals(data, that.data) &&
            Objects.equals(type, that.type) &&
            disposition == that.disposition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, data, type, disposition);
    }

    public enum Disposition {
        inline, attachment
    }

    public static final class Builder {
        private String name;
        private byte[] data;
        private String type;
        private Disposition disposition;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder inline() {
            this.disposition = Disposition.inline;
            return this;
        }

        public Builder attachment() {
            this.disposition = Disposition.attachment;
            return this;
        }

        public Builder disposition(Disposition disposition) {
            this.disposition = disposition;
            return this;
        }

        public Attachment build() {
            Attachment attachment = new Attachment();
            attachment.type = this.type;
            attachment.disposition = this.disposition;
            attachment.data = this.data;
            attachment.name = this.name;
            return attachment;
        }
    }
}
