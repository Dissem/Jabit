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
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.utils.Decode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Creates {@link NetworkMessage} objects from {@link InputStream InputStreams}
 */
public class Factory {
    public static NetworkMessage getNetworkMessage(int version, InputStream stream) throws IOException {
        return new V3MessageFactory().read(stream);
    }

    static ObjectPayload getObjectPayload(long objectType, long version, InputStream stream, int length) throws IOException {
        if (objectType < 4) {
            switch ((int) objectType) {
                case 0: // getpubkey
                    return new GetPubkey(Decode.bytes(stream, length));
                case 1: // pubkey
                    break;
                case 2: // msg
                    break;
                case 3: // broadcast
                    break;
            }
            throw new RuntimeException("This must not happen, someone broke something in the code!");
        } else {
            // passthrough message
            return new GenericPayload(Decode.bytes(stream, length));
        }
    }
}
