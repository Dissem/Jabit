/*
 * Copyright 2015 Christian Basler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.factory;

import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.utils.Decode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

/**
 * Creates {@link NetworkMessage} objects from {@link InputStream InputStreams}
 */
public class Factory {
    public static final Logger LOG = LoggerFactory.getLogger(Factory.class);

    public static NetworkMessage getNetworkMessage(int version, InputStream stream) throws SocketTimeoutException {
        try {
            return V3MessageFactory.read(stream);
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public static ObjectMessage getObjectMessage(int version, InputStream stream, int length) {
        try {
            return V3MessageFactory.readObject(stream, length);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    static ObjectPayload getObjectPayload(long objectType, long version, long streamNumber, InputStream stream, int length) throws IOException {
        if (objectType < 4) {
            switch ((int) objectType) {
                case 0:
                    return parseGetPubkey((int) version, streamNumber, stream, length);
                case 1:
                    return parsePubkey((int) version, streamNumber, stream, length);
                case 2:
                    return parseMsg((int) version, streamNumber, stream, length);
                case 3:
                    return parseBroadcast((int) version, streamNumber, stream, length);
                default:
                    LOG.error("This should not happen, someone broke something in the code!");
            }
        }
        // fallback: just store the message - we don't really care what it is
        LOG.warn("Unexpected object type: " + objectType);
        return GenericPayload.read(stream, streamNumber, length);
    }

    private static ObjectPayload parseGetPubkey(int version, long streamNumber, InputStream stream, int length) throws IOException {
        return GetPubkey.read(stream, streamNumber, length);
    }

    private static ObjectPayload parsePubkey(int version, long streamNumber, InputStream stream, int length) throws IOException {
        switch (version) {
            case 2:
                return V2Pubkey.read(stream, streamNumber);
            case 3:
                return V3Pubkey.read(stream, streamNumber);
            case 4:
                return V4Pubkey.read(stream, streamNumber, length);
        }
        LOG.debug("Unexpected pubkey version " + version + ", handling as generic payload object");
        return GenericPayload.read(stream, streamNumber, length);
    }

    private static ObjectPayload parseMsg(int version, long streamNumber, InputStream stream, int length) throws IOException {
        return Msg.read(stream, streamNumber, length);
    }

    private static ObjectPayload parseBroadcast(int version, long streamNumber, InputStream stream, int length) throws IOException {
        switch (version) {
            case 4:
                return V4Broadcast.read(stream, streamNumber, length);
            case 5:
                return V5Broadcast.read(stream, streamNumber, length);
            default:
                LOG.debug("Encountered unknown broadcast version " + version);
                return GenericPayload.read(stream, streamNumber, length);
        }
    }
}
