package ch.dissem.bitmessage.entity.payload;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by chrigu on 06.11.15.
 */
public class Ack extends ObjectPayload {
    private final long stream;
    private final byte[] data;

    public Ack(long version, long stream, byte[] data) {
        super(version);
        this.stream = stream;
        this.data = data;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.MSG;
    }

    @Override
    public long getStream() {
        return stream;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(data);
    }
}
