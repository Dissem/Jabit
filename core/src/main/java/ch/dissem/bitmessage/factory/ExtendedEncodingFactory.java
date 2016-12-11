package ch.dissem.bitmessage.factory;

import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.extended.Message;
import ch.dissem.bitmessage.entity.valueobject.extended.Vote;
import ch.dissem.bitmessage.exception.ApplicationException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/**
 * Factory that creates {@link ExtendedEncoding} objects from byte arrays. You can register your own types by adding a
 * {@link ExtendedEncoding.Unpacker} using {@link #registerFactory(ExtendedEncoding.Unpacker)}.
 */
public class ExtendedEncodingFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ExtendedEncodingFactory.class);
    private static final ExtendedEncodingFactory INSTANCE = new ExtendedEncodingFactory();
    private static final String KEY_MESSAGE_TYPE = "";
    private Map<String, ExtendedEncoding.Unpacker<?>> factories = new HashMap<>();

    private ExtendedEncodingFactory() {
        registerFactory(new Message.Unpacker());
        registerFactory(new Vote.Unpacker());
    }

    public void registerFactory(ExtendedEncoding.Unpacker<?> factory) {
        factories.put(factory.getType(), factory);
    }


    public ExtendedEncoding unzip(byte[] zippedData) {
        try (InflaterInputStream unzipper = new InflaterInputStream(new ByteArrayInputStream(zippedData))) {
            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(unzipper);
            int mapSize = unpacker.unpackMapHeader();
            String key = unpacker.unpackString();
            if (!KEY_MESSAGE_TYPE.equals(key)) {
                LOG.error("Unexpected content: " + key);
                return null;
            }
            String type = unpacker.unpackString();
            ExtendedEncoding.Unpacker<?> factory = factories.get(type);
            return new ExtendedEncoding(factory.unpack(unpacker, mapSize - 1));
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static ExtendedEncodingFactory getInstance() {
        return INSTANCE;
    }
}
