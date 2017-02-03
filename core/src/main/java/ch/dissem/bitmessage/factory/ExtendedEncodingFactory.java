package ch.dissem.bitmessage.factory;

import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.extended.Message;
import ch.dissem.bitmessage.entity.valueobject.extended.Vote;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.msgpack.Reader;
import ch.dissem.msgpack.types.MPMap;
import ch.dissem.msgpack.types.MPString;
import ch.dissem.msgpack.types.MPType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import static ch.dissem.bitmessage.utils.Strings.str;

/**
 * Factory that creates {@link ExtendedEncoding} objects from byte arrays. You can register your own types by adding a
 * {@link ExtendedEncoding.Unpacker} using {@link #registerFactory(ExtendedEncoding.Unpacker)}.
 */
public class ExtendedEncodingFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ExtendedEncodingFactory.class);
    private static final ExtendedEncodingFactory INSTANCE = new ExtendedEncodingFactory();
    private static final MPString KEY_MESSAGE_TYPE = new MPString("");
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
            Reader reader = Reader.getInstance();
            @SuppressWarnings("unchecked")
            MPMap<MPString, MPType<?>> map = (MPMap<MPString, MPType<?>>) reader.read(unzipper);
            MPType<?> messageType = map.get(KEY_MESSAGE_TYPE);
            if (messageType == null) {
                LOG.error("Missing message type");
                return null;
            }
            ExtendedEncoding.Unpacker<?> factory = factories.get(str(messageType));
            return new ExtendedEncoding(factory.unpack(map));
        } catch (ClassCastException | IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static ExtendedEncodingFactory getInstance() {
        return INSTANCE;
    }
}
