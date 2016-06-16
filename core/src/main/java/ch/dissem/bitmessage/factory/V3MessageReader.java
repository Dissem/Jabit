/*
 * Copyright 2016 Christian Basler
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

import ch.dissem.bitmessage.entity.MessagePayload;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.utils.Decode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.entity.NetworkMessage.MAGIC_BYTES;
import static ch.dissem.bitmessage.ports.NetworkHandler.MAX_PAYLOAD_SIZE;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * Similar to the {@link V3MessageFactory}, but used for NIO buffers which may or may not contain a whole message.
 */
public class V3MessageReader {
    private ReaderState state = ReaderState.MAGIC;
    private String command;
    private int length;
    private byte[] checksum;

    private List<NetworkMessage> messages = new LinkedList<>();

    public void update(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            switch (state) {
                case MAGIC:
                    if (!findMagicBytes(buffer)) return;
                    state = ReaderState.HEADER;
                case HEADER:
                    if (buffer.remaining() < 20) {
                        return;
                    }
                    command = getCommand(buffer);
                    length = (int) Decode.uint32(buffer);
                    if (length > MAX_PAYLOAD_SIZE) {
                        throw new NodeException("Payload of " + length + " bytes received, no more than " +
                                MAX_PAYLOAD_SIZE + " was expected.");
                    }
                    checksum = new byte[4];
                    buffer.get(checksum);
                    state = ReaderState.DATA;
                case DATA:
                    if (buffer.remaining() < length) {
                        return;
                    }
                    if (!testChecksum(buffer)) {
                        throw new NodeException("Checksum failed for message '" + command + "'");
                    }
                    try {
                        MessagePayload payload = V3MessageFactory.getPayload(
                                command,
                                new ByteArrayInputStream(buffer.array(), buffer.arrayOffset() + buffer.position(), length),
                                length);
                        if (payload != null) {
                            messages.add(new NetworkMessage(payload));
                        }
                    } catch (IOException e) {
                        throw new NodeException(e.getMessage());
                    }
                    state = ReaderState.MAGIC;
            }
        }
    }

    public List<NetworkMessage> getMessages() {
        return messages;
    }

    private boolean findMagicBytes(ByteBuffer buffer) {
        int i = 0;
        while (buffer.hasRemaining()) {
            if (i == 0) {
                buffer.mark();
            }
            if (buffer.get() == MAGIC_BYTES[i]) {
                i++;
                if (i == MAGIC_BYTES.length) {
                    return true;
                }
            } else {
                i = 0;
            }
        }
        if (i > 0) {
            buffer.reset();
        }
        return false;
    }

    private static String getCommand(ByteBuffer buffer) {
        int start = buffer.position();
        int l = 0;
        while (l < 12 && buffer.get() != 0) l++;
        int i = l + 1;
        while (i < 12) {
            if (buffer.get() != 0) throw new NodeException("'\\0' padding expected for command");
            i++;
        }
        try {
            return new String(buffer.array(), start, l, "ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new ApplicationException(e);
        }
    }

    private boolean testChecksum(ByteBuffer buffer) {
        byte[] payloadChecksum = cryptography().sha512(buffer.array(),
                buffer.arrayOffset() + buffer.position(), length);
        for (int i = 0; i < checksum.length; i++) {
            if (checksum[i] != payloadChecksum[i]) {
                return false;
            }
        }
        return true;
    }

    private enum ReaderState {MAGIC, HEADER, DATA}
}
