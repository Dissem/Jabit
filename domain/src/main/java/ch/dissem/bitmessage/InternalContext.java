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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Encrypted;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Security;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TreeSet;

/**
 * The internal context should normally only be used for port implementations. If you need it in your client
 * implementation, you're either doing something wrong, something very weird, or the BitmessageContext should
 * get extended.
 * <p/>
 * On the other hand, if you need the BitmessageContext in a port implementation, the same thing might apply.
 */
public class InternalContext {
    private final static Logger LOG = LoggerFactory.getLogger(InternalContext.class);

    private final Inventory inventory;
    private final NodeRegistry nodeRegistry;
    private final NetworkHandler networkHandler;
    private final AddressRepository addressRepository;
    private final MessageRepository messageRepository;
    private final ProofOfWorkEngine proofOfWorkEngine;

    private final TreeSet<Long> streams;
    private final int port;
    private long networkNonceTrialsPerByte = 1000;
    private long networkExtraBytes = 1000;

    public InternalContext(BitmessageContext.Builder builder) {
        this.inventory = builder.inventory;
        this.nodeRegistry = builder.nodeRegistry;
        this.networkHandler = builder.networkHandler;
        this.addressRepository = builder.addressRepo;
        this.messageRepository = builder.messageRepo;
        this.proofOfWorkEngine = builder.proofOfWorkEngine;

        port = builder.port;
        streams = builder.streams;

        init(inventory, nodeRegistry, networkHandler, addressRepository, messageRepository, proofOfWorkEngine);
    }

    private void init(Object... objects) {
        for (Object o : objects) {
            if (o instanceof ContextHolder) {
                ((ContextHolder) o).setContext(this);
            }
        }
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

    public void addStream(long stream) {
        streams.add(stream);
    }

    public void removeStream(long stream) {
        streams.remove(stream);
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
            long expires = UnixTime.now(+timeToLive);
            LOG.info("Expires at " + expires);
            ObjectMessage object = new ObjectMessage.Builder()
                    .stream(to.getStream())
                    .version(to.getVersion())
                    .expiresTime(expires)
                    .payload(payload)
                    .build();
            Security.doProofOfWork(object, proofOfWorkEngine, nonceTrialsPerByte, extraBytes);
            if (object.isSigned()) {
                object.sign(from.getPrivateKey());
            }
            if (object instanceof Encrypted) {
                object.encrypt(to.getPubkey());
            }
            inventory.storeObject(object);
            networkHandler.offer(object.getInventoryVector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface ContextHolder {
        void setContext(InternalContext context);
    }
}
