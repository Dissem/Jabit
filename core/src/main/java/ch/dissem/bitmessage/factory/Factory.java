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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.NodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * Creates {@link NetworkMessage} objects from {@link InputStream InputStreams}
 */
public class Factory {
    public static final Logger LOG = LoggerFactory.getLogger(Factory.class);

    public static NetworkMessage getNetworkMessage(int version, InputStream stream) throws SocketTimeoutException {
        try {
            return V3MessageFactory.read(stream);
        } catch (SocketTimeoutException | NodeException e) {
            throw e;
        } catch (SocketException e) {
            throw new NodeException(e.getMessage(), e);
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

    public static Pubkey createPubkey(long version, long stream, byte[] publicSigningKey, byte[] publicEncryptionKey,
                                      long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
        return createPubkey(version, stream, publicSigningKey, publicEncryptionKey, nonceTrialsPerByte, extraBytes,
                Pubkey.Feature.bitfield(features));
    }

    public static Pubkey createPubkey(long version, long stream, byte[] publicSigningKey, byte[] publicEncryptionKey,
                                      long nonceTrialsPerByte, long extraBytes, int behaviourBitfield) {
        if (publicSigningKey.length != 64 && publicSigningKey.length != 65)
            throw new IllegalArgumentException("64 bytes signing key expected, but it was "
                    + publicSigningKey.length + " bytes long.");
        if (publicEncryptionKey.length != 64 && publicEncryptionKey.length != 65)
            throw new IllegalArgumentException("64 bytes encryption key expected, but it was "
                    + publicEncryptionKey.length + " bytes long.");

        switch ((int) version) {
            case 2:
                return new V2Pubkey.Builder()
                        .stream(stream)
                        .publicSigningKey(publicSigningKey)
                        .publicEncryptionKey(publicEncryptionKey)
                        .behaviorBitfield(behaviourBitfield)
                        .build();
            case 3:
                return new V3Pubkey.Builder()
                        .stream(stream)
                        .publicSigningKey(publicSigningKey)
                        .publicEncryptionKey(publicEncryptionKey)
                        .behaviorBitfield(behaviourBitfield)
                        .nonceTrialsPerByte(nonceTrialsPerByte)
                        .extraBytes(extraBytes)
                        .build();
            case 4:
                return new V4Pubkey(
                        new V3Pubkey.Builder()
                                .stream(stream)
                                .publicSigningKey(publicSigningKey)
                                .publicEncryptionKey(publicEncryptionKey)
                                .behaviorBitfield(behaviourBitfield)
                                .nonceTrialsPerByte(nonceTrialsPerByte)
                                .extraBytes(extraBytes)
                                .build()
                );
            default:
                throw new IllegalArgumentException("Unexpected pubkey version " + version);
        }
    }

    public static BitmessageAddress createIdentityFromPrivateKey(String address,
                                                                 byte[] privateSigningKey, byte[] privateEncryptionKey,
                                                                 long nonceTrialsPerByte, long extraBytes,
                                                                 int behaviourBitfield) {
        BitmessageAddress temp = new BitmessageAddress(address);
        PrivateKey privateKey = new PrivateKey(privateSigningKey, privateEncryptionKey,
                createPubkey(temp.getVersion(), temp.getStream(),
                        security().createPublicKey(privateSigningKey),
                        security().createPublicKey(privateEncryptionKey),
                        nonceTrialsPerByte, extraBytes, behaviourBitfield));
        BitmessageAddress result = new BitmessageAddress(privateKey);
        if (!result.getAddress().equals(address)) {
            throw new IllegalArgumentException("Address not matching private key. Address: " + address
                    + "; Address derived from private key: " + result.getAddress());
        }
        return result;
    }

    public static BitmessageAddress generatePrivateAddress(boolean shorter,
                                                           long stream,
                                                           Pubkey.Feature... features) {
        return new BitmessageAddress(new PrivateKey(shorter, stream, 1000, 1000, features));
    }

    static ObjectPayload getObjectPayload(long objectType,
                                          long version,
                                          long streamNumber,
                                          InputStream stream,
                                          int length) throws IOException {
        ObjectType type = ObjectType.fromNumber(objectType);
        if (type != null) {
            switch (type) {
                case GET_PUBKEY:
                    return parseGetPubkey(version, streamNumber, stream, length);
                case PUBKEY:
                    return parsePubkey(version, streamNumber, stream, length);
                case MSG:
                    return parseMsg(version, streamNumber, stream, length);
                case BROADCAST:
                    return parseBroadcast(version, streamNumber, stream, length);
                default:
                    LOG.error("This should not happen, someone broke something in the code!");
            }
        }
        // fallback: just store the message - we don't really care what it is
        LOG.trace("Unexpected object type: " + objectType);
        return GenericPayload.read(version, stream, streamNumber, length);
    }

    private static ObjectPayload parseGetPubkey(long version, long streamNumber, InputStream stream, int length) throws IOException {
        return GetPubkey.read(stream, streamNumber, length, version);
    }

    public static Pubkey readPubkey(long version, long stream, InputStream is, int length, boolean encrypted) throws IOException {
        switch ((int) version) {
            case 2:
                return V2Pubkey.read(is, stream);
            case 3:
                return V3Pubkey.read(is, stream);
            case 4:
                return V4Pubkey.read(is, stream, length, encrypted);
        }
        LOG.debug("Unexpected pubkey version " + version + ", handling as generic payload object");
        return null;
    }

    private static ObjectPayload parsePubkey(long version, long streamNumber, InputStream stream, int length) throws IOException {
        Pubkey pubkey = readPubkey(version, streamNumber, stream, length, true);
        return pubkey != null ? pubkey : GenericPayload.read(version, stream, streamNumber, length);
    }

    private static ObjectPayload parseMsg(long version, long streamNumber, InputStream stream, int length) throws IOException {
        return Msg.read(stream, streamNumber, length);
    }

    private static ObjectPayload parseBroadcast(long version, long streamNumber, InputStream stream, int length) throws IOException {
        switch ((int) version) {
            case 4:
                return V4Broadcast.read(stream, streamNumber, length);
            case 5:
                return V5Broadcast.read(stream, streamNumber, length);
            default:
                LOG.debug("Encountered unknown broadcast version " + version);
                return GenericPayload.read(version, stream, streamNumber, length);
        }
    }

    public static Broadcast getBroadcast(Plaintext plaintext) {
        BitmessageAddress sendingAddress = plaintext.getFrom();
        if (sendingAddress.getVersion() < 4) {
            return new V4Broadcast(sendingAddress, plaintext);
        } else {
            return new V5Broadcast(sendingAddress, plaintext);
        }
    }
}
