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
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.utils.Decode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Creates {@link NetworkMessage} objects from {@link InputStream InputStreams}
 */
public class Factory {
    public static final Logger LOG = LoggerFactory.getLogger(Factory.class);

    public static NetworkMessage getNetworkMessage(int version, InputStream stream) throws IOException {
        return new V3MessageFactory().read(stream);
    }

    static ObjectPayload getObjectPayload(long objectType, long version, long streamNumber, InputStream stream, int length) throws IOException {
        if (objectType < 4) {
            switch ((int) objectType) {
                case 0: // getpubkey
                    return parseGetPubkey((int) version, streamNumber, stream, length);
                case 1: // pubkey
                    return parsePubkey((int) version, streamNumber, stream, length);
                case 2: // msg
                    return parseMsg((int) version, streamNumber, stream, length);
                case 3: // broadcast
                    return parseBroadcast((int) version, streamNumber, stream, length);
            }
            LOG.error("This should not happen, someone broke something in the code!");
        }
        // fallback: just store the message - we don't really care what it is
        LOG.error("Unexpected object type: " + objectType);
        return new GenericPayload(streamNumber, Decode.bytes(stream, length));
    }

    private static ObjectPayload parseGetPubkey(int version, long streamNumber, InputStream stream, int length) throws IOException {
        return new GetPubkey(streamNumber, Decode.bytes(stream, length));
    }

    private static ObjectPayload parsePubkey(int version, long streamNumber, InputStream stream, int length) throws IOException {
        switch (version) {
            case 2:
                return new V2Pubkey.Builder()
                        .streamNumber(streamNumber)
                        .behaviorBitfield((int) Decode.int64(stream))
                        .publicSigningKey(Decode.bytes(stream, 64))
                        .publicEncryptionKey(Decode.bytes(stream, 64))
                        .build();
            case 3:
                V3Pubkey.Builder v3 = new V3Pubkey.Builder()
                        .streamNumber(streamNumber)
                        .behaviorBitfield((int) Decode.int64(stream))
                        .publicSigningKey(Decode.bytes(stream, 64))
                        .publicEncryptionKey(Decode.bytes(stream, 64))
                        .nonceTrialsPerByte(Decode.varInt(stream))
                        .extraBytes(Decode.varInt(stream));
                int sigLength = (int) Decode.varInt(stream);
                v3.signature(Decode.bytes(stream, sigLength));
                return v3.build();
            case 4:
                return new V4Pubkey(streamNumber, Decode.bytes(stream, 32), Decode.bytes(stream, length - 32));
        }
        LOG.debug("Unexpected pubkey version " + version + ", handling as generic payload object");
        return new GenericPayload(streamNumber, Decode.bytes(stream, length));
    }

    private static ObjectPayload parseMsg(int version, long streamNumber, InputStream stream, int length) throws IOException {
        return new Msg(streamNumber, Decode.bytes(stream, length));
    }

    private static ObjectPayload parseBroadcast(int version, long streamNumber, InputStream stream, int length) throws IOException {
        switch (version) {
            case 4:
                return new V4Broadcast(streamNumber, Decode.bytes(stream, length));
            case 5:
                return new V5Broadcast(streamNumber, Decode.bytes(stream, 32), Decode.bytes(stream, length - 32));
            default:
                LOG.debug("Encountered unknown broadcast version " + version);
                return new GenericPayload(streamNumber, Decode.bytes(stream, length));
        }
    }
}
