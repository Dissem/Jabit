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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.utils.Encode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static ch.dissem.bitmessage.utils.Security.sha512;

/**
 * A network message is exchanged between two nodes.
 */
public class NetworkMessage implements Streamable {
    /**
     * Magic value indicating message origin network, and used to seek to next message when stream state is unknown
     */
    public final static int MAGIC = 0xE9BEB4D9;
    public final static byte[] MAGIC_BYTES = ByteBuffer.allocate(4).putInt(MAGIC).array();

    private final NetworkAddress targetNode;

    private final Command payload;

    public NetworkMessage(NetworkAddress target, Command payload) {
        this.targetNode = target;
        this.payload = payload;
    }

    /**
     * First 4 bytes of sha512(payload)
     */
    private byte[] getChecksum(byte[] bytes) throws NoSuchProviderException, NoSuchAlgorithmException {
        byte[] d = sha512(bytes);
        return new byte[]{d[0], d[1], d[2], d[3]};
    }

    /**
     * The actual data, a message or an object. Not to be confused with objectPayload.
     */
    public Command getPayload() {
        return payload;
    }

    public NetworkAddress getTargetNode() {
        return targetNode;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        // magic
        Encode.int32(MAGIC, stream);

        // ASCII string identifying the packet content, NULL padded (non-NULL padding results in packet rejected)
        stream.write(payload.getCommand().getBytes("ASCII"));
        for (int i = payload.getCommand().length(); i < 12; i++) {
            stream.write('\0');
        }

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
        payload.write(payloadStream);
        byte[] payloadBytes = payloadStream.toByteArray();

        // Length of payload in number of bytes. Because of other restrictions, there is no reason why this length would
        // ever be larger than 1600003 bytes. Some clients include a sanity-check to avoid processing messages which are
        // larger than this.
        Encode.int32(payloadBytes.length, stream);

        // checksum
        try {
            stream.write(getChecksum(payloadBytes));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        // message payload
        stream.write(payloadBytes);
    }
}
