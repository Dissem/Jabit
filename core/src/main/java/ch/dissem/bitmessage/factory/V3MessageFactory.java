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

import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.utils.AccessCounter;
import ch.dissem.bitmessage.utils.Decode;
import ch.dissem.bitmessage.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static ch.dissem.bitmessage.entity.NetworkMessage.MAGIC_BYTES;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * Creates protocol v3 network messages from {@link InputStream InputStreams}
 */
class V3MessageFactory {
    private static Logger LOG = LoggerFactory.getLogger(V3MessageFactory.class);

    public static NetworkMessage read(InputStream in) throws IOException {
        findMagic(in);
        String command = getCommand(in);
        int length = (int) Decode.uint32(in);
        if (length > 1600003) {
            throw new NodeException("Payload of " + length + " bytes received, no more than 1600003 was expected.");
        }
        byte[] checksum = Decode.bytes(in, 4);

        byte[] payloadBytes = Decode.bytes(in, length);

        if (testChecksum(checksum, payloadBytes)) {
            MessagePayload payload = getPayload(command, new ByteArrayInputStream(payloadBytes), length);
            if (payload != null)
                return new NetworkMessage(payload);
            else
                return null;
        } else {
            throw new IOException("Checksum failed for message '" + command + "'");
        }
    }

    static MessagePayload getPayload(String command, InputStream stream, int length) throws IOException {
        switch (command) {
            case "version":
                return parseVersion(stream);
            case "verack":
                return new VerAck();
            case "addr":
                return parseAddr(stream);
            case "inv":
                return parseInv(stream);
            case "getdata":
                return parseGetData(stream);
            case "object":
                return readObject(stream, length);
            case "custom":
                return readCustom(stream, length);
            default:
                LOG.debug("Unknown command: " + command);
                return null;
        }
    }

    private static MessagePayload readCustom(InputStream in, int length) throws IOException {
        return CustomMessage.read(in, length);
    }

    public static ObjectMessage readObject(InputStream in, int length) throws IOException {
        AccessCounter counter = new AccessCounter();
        byte nonce[] = Decode.bytes(in, 8, counter);
        long expiresTime = Decode.int64(in, counter);
        long objectType = Decode.uint32(in, counter);
        long version = Decode.varInt(in, counter);
        long stream = Decode.varInt(in, counter);

        byte[] data = Decode.bytes(in, length - counter.length());
        ObjectPayload payload;
        try {
            ByteArrayInputStream dataStream = new ByteArrayInputStream(data);
            payload = Factory.getObjectPayload(objectType, version, stream, dataStream, data.length);
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Could not parse object payload - using generic payload instead", e);
                LOG.trace(Strings.hex(data).toString());
            }
            payload = new GenericPayload(version, stream, data);
        }

        return new ObjectMessage.Builder()
            .nonce(nonce)
            .expiresTime(expiresTime)
            .objectType(objectType)
            .stream(stream)
            .payload(payload)
            .build();
    }

    private static GetData parseGetData(InputStream stream) throws IOException {
        long count = Decode.varInt(stream);
        GetData.Builder builder = new GetData.Builder();
        for (int i = 0; i < count; i++) {
            builder.addInventoryVector(parseInventoryVector(stream));
        }
        return builder.build();
    }

    private static Inv parseInv(InputStream stream) throws IOException {
        long count = Decode.varInt(stream);
        Inv.Builder builder = new Inv.Builder();
        for (int i = 0; i < count; i++) {
            builder.addInventoryVector(parseInventoryVector(stream));
        }
        return builder.build();
    }

    private static Addr parseAddr(InputStream stream) throws IOException {
        long count = Decode.varInt(stream);
        Addr.Builder builder = new Addr.Builder();
        for (int i = 0; i < count; i++) {
            builder.addAddress(parseAddress(stream, false));
        }
        return builder.build();
    }

    private static Version parseVersion(InputStream stream) throws IOException {
        int version = Decode.int32(stream);
        long services = Decode.int64(stream);
        long timestamp = Decode.int64(stream);
        NetworkAddress addrRecv = parseAddress(stream, true);
        NetworkAddress addrFrom = parseAddress(stream, true);
        long nonce = Decode.int64(stream);
        String userAgent = Decode.varString(stream);
        long[] streamNumbers = Decode.varIntList(stream);

        return new Version.Builder()
            .version(version)
            .services(services)
            .timestamp(timestamp)
            .addrRecv(addrRecv).addrFrom(addrFrom)
            .nonce(nonce)
            .userAgent(userAgent)
            .streams(streamNumbers).build();
    }

    private static InventoryVector parseInventoryVector(InputStream stream) throws IOException {
        return InventoryVector.fromHash(Decode.bytes(stream, 32));
    }

    private static NetworkAddress parseAddress(InputStream stream, boolean light) throws IOException {
        long time;
        long streamNumber;
        if (!light) {
            time = Decode.int64(stream);
            streamNumber = Decode.uint32(stream); // This isn't consistent, not sure if this is correct
        } else {
            time = 0;
            streamNumber = 0;
        }
        long services = Decode.int64(stream);
        byte[] ipv6 = Decode.bytes(stream, 16);
        int port = Decode.uint16(stream);
        return new NetworkAddress.Builder()
            .time(time)
            .stream(streamNumber)
            .services(services)
            .ipv6(ipv6)
            .port(port)
            .build();
    }

    private static boolean testChecksum(byte[] checksum, byte[] payload) {
        byte[] payloadChecksum = cryptography().sha512(payload);
        for (int i = 0; i < checksum.length; i++) {
            if (checksum[i] != payloadChecksum[i]) {
                return false;
            }
        }
        return true;
    }

    private static String getCommand(InputStream stream) throws IOException {
        byte[] bytes = new byte[12];
        int end = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) stream.read();
            if (end == bytes.length) {
                if (bytes[i] == 0) end = i;
            } else {
                if (bytes[i] != 0) throw new IOException("'\\0' padding expected for command");
            }
        }
        return new String(bytes, 0, end, "ASCII");
    }

    private static void findMagic(InputStream in) throws IOException {
        int pos = 0;
        for (int i = 0; i < 1620000; i++) {
            byte b = (byte) in.read();
            if (b == MAGIC_BYTES[pos]) {
                if (pos + 1 == MAGIC_BYTES.length) {
                    return;
                }
            } else if (pos > 0 && b == MAGIC_BYTES[0]) {
                pos = 1;
            } else {
                pos = 0;
            }
            pos++;
        }
        throw new NodeException("Failed to find MAGIC bytes in stream");
    }
}
