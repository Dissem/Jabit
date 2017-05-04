package ch.dissem.bitmessage.entity.valueobject;

import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.msgpack.types.MPMap;
import ch.dissem.msgpack.types.MPString;
import ch.dissem.msgpack.types.MPType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;

/**
 * Extended encoding message object.
 */
public class ExtendedEncoding implements Serializable {
    private static final long serialVersionUID = 3876871488247305200L;
    private static final Logger LOG = LoggerFactory.getLogger(ExtendedEncoding.class);

    private ExtendedType content;

    public ExtendedEncoding(ExtendedType content) {
        this.content = content;
    }

    public String getType() {
        if (content == null) {
            return null;
        } else {
            return content.getType();
        }
    }

    public ExtendedType getContent() {
        return content;
    }

    public byte[] zip() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (DeflaterOutputStream zipper = new DeflaterOutputStream(out)) {
                content.pack().pack(zipper);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtendedEncoding that = (ExtendedEncoding) o;
        return Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content);
    }

    public interface Unpacker<T extends ExtendedType> {
        String getType();

        T unpack(MPMap<MPString, MPType<?>> map);
    }

    public interface ExtendedType extends Serializable {
        String getType();

        MPMap<MPString, MPType<?>> pack() throws IOException;
    }
}
