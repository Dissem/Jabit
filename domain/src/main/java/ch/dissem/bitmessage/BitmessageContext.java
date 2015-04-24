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

import ch.dissem.bitmessage.ports.AddressRepository;
import ch.dissem.bitmessage.ports.Inventory;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.ports.NodeRegistry;

import java.util.Collection;
import java.util.TreeSet;

/**
 * Created by chris on 05.04.15.
 */
public class BitmessageContext {
    public static final int CURRENT_VERSION = 3;

    private Inventory inventory;
    private NodeRegistry nodeRegistry;
    private NetworkHandler networkHandler;
    private AddressRepository addressRepo;

    private Collection<Long> streams = new TreeSet<>();

    private int port;

    private long networkNonceTrialsPerByte = 1000;
    private long networkExtraBytes = 1000;

    private BitmessageContext(Builder builder) {
        port = builder.port;
        inventory = builder.inventory;
        nodeRegistry = builder.nodeRegistry;
        networkHandler = builder.networkHandler;
        addressRepo = builder.addressRepo;
        streams = builder.streams;

        init(inventory, nodeRegistry, networkHandler, addressRepo);
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
        private Collection<Long> streams;

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

        public Builder streams(Collection<Long> streams) {
            this.streams = streams;
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
            return new BitmessageContext(this);
        }

        private void nonNull(String name, Object o) {
            if (o == null) throw new IllegalStateException(name + " must not be null");
        }
    }
}
