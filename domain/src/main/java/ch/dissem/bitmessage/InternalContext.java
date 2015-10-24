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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.payload.Broadcast;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TreeSet;

import static ch.dissem.bitmessage.utils.UnixTime.DAY;

/**
 * The internal context should normally only be used for port implementations. If you need it in your client
 * implementation, you're either doing something wrong, something very weird, or the BitmessageContext should
 * get extended.
 * <p>
 * On the other hand, if you need the BitmessageContext in a port implementation, the same thing might apply.
 * </p>
 */
public class InternalContext {
    private final static Logger LOG = LoggerFactory.getLogger(InternalContext.class);

    private final Security security;
    private final Inventory inventory;
    private final NodeRegistry nodeRegistry;
    private final NetworkHandler networkHandler;
    private final AddressRepository addressRepository;
    private final MessageRepository messageRepository;
    private final ProofOfWorkEngine proofOfWorkEngine;
    private final MessageCallback messageCallback;

    private final TreeSet<Long> streams = new TreeSet<>();
    private final int port;
    private final long clientNonce;
    private final long networkNonceTrialsPerByte = 1000;
    private final long networkExtraBytes = 1000;
    private long connectionTTL;
    private int connectionLimit;

    public InternalContext(BitmessageContext.Builder builder) {
        this.security = builder.security;
        this.inventory = builder.inventory;
        this.nodeRegistry = builder.nodeRegistry;
        this.networkHandler = builder.networkHandler;
        this.addressRepository = builder.addressRepo;
        this.messageRepository = builder.messageRepo;
        this.proofOfWorkEngine = builder.proofOfWorkEngine;
        this.clientNonce = security.randomNonce();
        this.messageCallback = builder.messageCallback;
        this.port = builder.port;
        this.connectionLimit = builder.connectionLimit;
        this.connectionTTL = builder.connectionTTL;

        Singleton.initialize(security);

        // TODO: streams of new identities and subscriptions should also be added. This works only after a restart.
        for (BitmessageAddress address : addressRepository.getIdentities()) {
            streams.add(address.getStream());
        }
        for (BitmessageAddress address : addressRepository.getSubscriptions()) {
            streams.add(address.getStream());
        }
        if (streams.isEmpty()) {
            streams.add(1L);
        }

        init(security, inventory, nodeRegistry, networkHandler, addressRepository, messageRepository, proofOfWorkEngine);
        for (BitmessageAddress identity : addressRepository.getIdentities()) {
            streams.add(identity.getStream());
        }
    }

    private void init(Object... objects) {
        for (Object o : objects) {
            if (o instanceof ContextHolder) {
                ((ContextHolder) o).setContext(this);
            }
        }
    }

    public Security getSecurity() {
        return security;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public NodeRegistry getNodeRegistry() {
        return nodeRegistry;
    }

    public NetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public AddressRepository getAddressRepo() {
        return addressRepository;
    }

    public MessageRepository getMessageRepository() {
        return messageRepository;
    }

    public ProofOfWorkEngine getProofOfWorkEngine() {
        return proofOfWorkEngine;
    }

    public long[] getStreams() {
        long[] result = new long[streams.size()];
        int i = 0;
        for (long stream : streams) {
            result[i++] = stream;
        }
        return result;
    }

    public int getPort() {
        return port;
    }

    public long getNetworkNonceTrialsPerByte() {
        return networkNonceTrialsPerByte;
    }

    public long getNonceTrialsPerByte(BitmessageAddress address) {
        long nonceTrialsPerByte = address.getPubkey().getNonceTrialsPerByte();
        return networkNonceTrialsPerByte > nonceTrialsPerByte ? networkNonceTrialsPerByte : nonceTrialsPerByte;
    }

    public long getNetworkExtraBytes() {
        return networkExtraBytes;
    }

    public long getExtraBytes(BitmessageAddress address) {
        long extraBytes = address.getPubkey().getExtraBytes();
        return networkExtraBytes > extraBytes ? networkExtraBytes : extraBytes;
    }

    public void send(BitmessageAddress from, BitmessageAddress to, ObjectPayload payload, long timeToLive, long nonceTrialsPerByte, long extraBytes) {
        try {
            if (to == null) to = from;
            long expires = UnixTime.now(+timeToLive);
            LOG.info("Expires at " + expires);
            ObjectMessage object = new ObjectMessage.Builder()
                    .stream(to.getStream())
                    .expiresTime(expires)
                    .payload(payload)
                    .build();
            if (object.isSigned()) {
                object.sign(from.getPrivateKey());
            }
            if (payload instanceof Broadcast) {
                ((Broadcast) payload).encrypt();
            } else if (payload instanceof Encrypted) {
                object.encrypt(to.getPubkey());
            }
            messageCallback.proofOfWorkStarted(payload);
            security.doProofOfWork(object, nonceTrialsPerByte, extraBytes);
            messageCallback.proofOfWorkCompleted(payload);
            if (payload instanceof PlaintextHolder) {
                Plaintext plaintext = ((PlaintextHolder) payload).getPlaintext();
                plaintext.setInventoryVector(object.getInventoryVector());
                messageRepository.save(plaintext);
            }
            inventory.storeObject(object);
            networkHandler.offer(object.getInventoryVector());
            messageCallback.messageOffered(payload, object.getInventoryVector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPubkey(BitmessageAddress identity, long targetStream) {
        try {
            long expires = UnixTime.now(+28 * DAY);
            LOG.info("Expires at " + expires);
            ObjectMessage response = new ObjectMessage.Builder()
                    .stream(targetStream)
                    .expiresTime(expires)
                    .payload(identity.getPubkey())
                    .build();
            response.sign(identity.getPrivateKey());
            response.encrypt(security.createPublicKey(identity.getPublicDecryptionKey()));
            messageCallback.proofOfWorkStarted(identity.getPubkey());
            security.doProofOfWork(response, networkNonceTrialsPerByte, networkExtraBytes);
            messageCallback.proofOfWorkCompleted(identity.getPubkey());
            inventory.storeObject(response);
            networkHandler.offer(response.getInventoryVector());
            // TODO: save that the pubkey was just sent, and on which stream!
            messageCallback.messageOffered(identity.getPubkey(), response.getInventoryVector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void requestPubkey(BitmessageAddress contact) {
        long expires = UnixTime.now(+2 * DAY);
        LOG.info("Expires at " + expires);
        ObjectMessage response = new ObjectMessage.Builder()
                .stream(contact.getStream())
                .expiresTime(expires)
                .payload(new GetPubkey(contact))
                .build();
        messageCallback.proofOfWorkStarted(response.getPayload());
        security.doProofOfWork(response, networkNonceTrialsPerByte, networkExtraBytes);
        messageCallback.proofOfWorkCompleted(response.getPayload());
        inventory.storeObject(response);
        networkHandler.offer(response.getInventoryVector());
        messageCallback.messageOffered(response.getPayload(), response.getInventoryVector());
    }

    public long getClientNonce() {
        return clientNonce;
    }

    public long getConnectionTTL() {
        return connectionTTL;
    }

    public int getConnectionLimit() {
        return connectionLimit;
    }

    public interface ContextHolder {
        void setContext(InternalContext context);
    }
}
