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

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Security;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Created by chris on 05.04.15.
 */
public class BitmessageContext {
    public static final int CURRENT_VERSION = 3;
    private final static Logger LOG = LoggerFactory.getLogger(BitmessageContext.class);
    private final Inventory inventory;
    private final NodeRegistry nodeRegistry;
    private final NetworkHandler networkHandler;
    private final AddressRepository addressRepo;
    private final ProofOfWorkEngine proofOfWorkEngine;

    private final TreeSet<Long> streams;

    private final int port;

    private long networkNonceTrialsPerByte = 1000;
    private long networkExtraBytes = 1000;

    private BitmessageContext(Builder builder) {
        port = builder.port;
        inventory = builder.inventory;
        nodeRegistry = builder.nodeRegistry;
        networkHandler = builder.networkHandler;
        addressRepo = builder.addressRepo;
        proofOfWorkEngine = builder.proofOfWorkEngine;
        streams = builder.streams;

        init(inventory, nodeRegistry, networkHandler, addressRepo, proofOfWorkEngine);
    }

    private void init(Object... objects) {
        for (Object o : objects) {
            if (o instanceof ContextHolder) {
                ((ContextHolder) o).setContext(this);
            }
        }
    }

    public void send(long stream, long version, ObjectPayload payload, long timeToLive, long nonceTrialsPerByte, long extraBytes) throws IOException {
        long expires = UnixTime.now(+timeToLive);
        LOG.info("Expires at " + expires);
        ObjectMessage object = new ObjectMessage.Builder()
                .stream(stream)
                .version(version)
                .expiresTime(expires)
                .payload(payload)
                .build();
        Security.doProofOfWork(object, proofOfWorkEngine, nonceTrialsPerByte, extraBytes);
        inventory.storeObject(object);
        networkHandler.offer(object.getInventoryVector());
    }

    public Inventory getInventory() {
        return inventory;
    }

    public NodeRegistry getAddressRepository() {
        return nodeRegistry;
    }

    public NetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public int getPort() {
        return port;
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

    public long getNetworkNonceTrialsPerByte() {
        return networkNonceTrialsPerByte;
    }

    public long getNetworkExtraBytes() {
        return networkExtraBytes;
    }


    public interface ContextHolder {
        void setContext(BitmessageContext context);
    }

    public static final class Builder {
        private int port = 8444;
        private Inventory inventory;
        private NodeRegistry nodeRegistry;
        private NetworkHandler networkHandler;
        private AddressRepository addressRepo;
        private ProofOfWorkEngine proofOfWorkEngine;
        private TreeSet<Long> streams;

        public Builder() {
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder inventory(Inventory inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder nodeRegistry(NodeRegistry nodeRegistry) {
            this.nodeRegistry = nodeRegistry;
            return this;
        }

        public Builder networkHandler(NetworkHandler networkHandler) {
            this.networkHandler = networkHandler;
            return this;
        }

        public Builder addressRepo(AddressRepository addressRepo) {
            this.addressRepo = addressRepo;
            return this;
        }

        public Builder proofOfWorkEngine(ProofOfWorkEngine proofOfWorkEngine) {
            this.proofOfWorkEngine = proofOfWorkEngine;
            return this;
        }

        public Builder streams(Collection<Long> streams) {
            this.streams = new TreeSet<>(streams);
            return this;
        }

        public Builder streams(long... streams) {
            this.streams = new TreeSet<>();
            for (long stream : streams) {
                this.streams.add(stream);
            }
            return this;
        }

        public BitmessageContext build() {
            nonNull("inventory", inventory);
            nonNull("nodeRegistry", nodeRegistry);
            nonNull("networkHandler", networkHandler);
            nonNull("addressRepo", addressRepo);
            if (streams == null) {
                streams(1);
            }
            if (proofOfWorkEngine == null) {
                proofOfWorkEngine = new MultiThreadedPOWEngine();
            }
            return new BitmessageContext(this);
        }

        private void nonNull(String name, Object o) {
            if (o == null) throw new IllegalStateException(name + " must not be null");
        }
    }
}
