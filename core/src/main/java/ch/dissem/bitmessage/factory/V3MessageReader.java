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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static ch.dissem.bitmessage.entity.NetworkMessage.MAGIC_BYTES;
import static ch.dissem.bitmessage.factory.BufferPool.bufferPool;
import static ch.dissem.bitmessage.ports.NetworkHandler.MAX_PAYLOAD_SIZE;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * Similar to the {@link V3MessageFactory}, but used for NIO buffers which may or may not contain a whole message.
 */
public class V3MessageReader {
    private static final Logger LOG = LoggerFactory.getLogger(V3MessageReader.class);

    private ByteBuffer headerBuffer;
    private ByteBuffer dataBuffer;

    private ReaderState state = ReaderState.MAGIC;
    private String command;
    private int length;
    private byte[] checksum;

    private List<NetworkMessage> messages = new LinkedList<>();
    private SizeInfo sizeInfo = new SizeInfo();

    public ByteBuffer getActiveBuffer() {
        if (state != null && state != ReaderState.DATA) {
            if (headerBuffer == null) {
                headerBuffer = bufferPool.allocateHeaderBuffer();
            }
        }
        return state == ReaderState.DATA ? dataBuffer : headerBuffer;
    }

    public void update() {
        if (state != ReaderState.DATA) {
            getActiveBuffer();
            headerBuffer.flip();
        }
        switch (state) {
            case MAGIC:
                if (!findMagicBytes(headerBuffer)) {
                    headerBuffer.compact();
                    return;
                }
                state = ReaderState.HEADER;
            case HEADER:
                if (headerBuffer.remaining() < 20) {
                    headerBuffer.compact();
                    headerBuffer.limit(20);
                    return;
                }
                command = getCommand(headerBuffer);
                length = (int) Decode.uint32(headerBuffer);
                if (length > MAX_PAYLOAD_SIZE) {
                    throw new NodeException("Payload of " + length + " bytes received, no more than " +
                        MAX_PAYLOAD_SIZE + " was expected.");
                }
                sizeInfo.add(length); // FIXME: remove this once we have some values to work with
                checksum = new byte[4];
                headerBuffer.get(checksum);
                state = ReaderState.DATA;
                bufferPool.deallocate(headerBuffer);
                headerBuffer = null;
                dataBuffer = bufferPool.allocate(length);
                dataBuffer.clear();
                dataBuffer.limit(length);
            case DATA:
                if (dataBuffer.position() < length) {
                    return;
                } else {
                    dataBuffer.flip();
                }
                if (!testChecksum(dataBuffer)) {
                    state = ReaderState.MAGIC;
                    throw new NodeException("Checksum failed for message '" + command + "'");
                }
                try {
                    MessagePayload payload = V3MessageFactory.getPayload(
                        command,
                        new ByteArrayInputStream(dataBuffer.array(),
                            dataBuffer.arrayOffset() + dataBuffer.position(), length),
                        length);
                    if (payload != null) {
                        messages.add(new NetworkMessage(payload));
                    }
                } catch (IOException e) {
                    throw new NodeException(e.getMessage());
                } finally {
                    state = ReaderState.MAGIC;
                    bufferPool.deallocate(dataBuffer);
                    dataBuffer = null;
                    dataBuffer = null;
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

    /**
     * De-allocates all buffers. This method should be called iff the reader isn't used anymore, i.e. when its
     * connection is severed.
     */
    public void cleanup() {
        state = null;
        if (headerBuffer != null) {
            bufferPool.deallocate(headerBuffer);
        }
        if (dataBuffer != null) {
            bufferPool.deallocate(dataBuffer);
        }
    }

    private enum ReaderState {MAGIC, HEADER, DATA}

    private class SizeInfo {
        private FileWriter file;
        private long min = Long.MAX_VALUE;
        private long avg = 0;
        private long max = Long.MIN_VALUE;
        private long count = 0;

        private SizeInfo() {
            try {
                file = new FileWriter("D:/message_size_info-" + UUID.randomUUID() + ".csv");
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        private void add(long length) {
            avg = (count * avg + length) / (count + 1);
            if (length < min) {
                min = length;
            }
            if (length > max) {
                max = length;
            }
            count++;
            LOG.info("Received message with data size " + length + "; Min: " + min + "; Max: " + max + "; Avg: " + avg);
            try {
                file.write(length + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
