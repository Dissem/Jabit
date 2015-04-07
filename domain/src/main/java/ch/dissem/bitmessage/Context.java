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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

/**
 * Created by chris on 05.04.15.
 */
public class Context {
    public static final int CURRENT_VERSION = 3;

    private static Context instance;

    private Inventory inventory;
    private AddressRepository addressRepo;
    private NetworkHandler networkHandler;

    private Collection<Long> streams = new TreeSet<>();

    private int port;

    private Context(Inventory inventory, AddressRepository addressRepo,
                    NetworkHandler networkHandler, int port) {
        this.inventory = inventory;
        this.addressRepo = addressRepo;
        this.networkHandler = networkHandler;
        this.port = port;
    }

    public static void init(Inventory inventory, AddressRepository addressRepository, NetworkHandler networkHandler, int port) {
        instance = new Context(inventory, addressRepository, networkHandler, port);
    }

    public static Context getInstance() {
        return instance;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public AddressRepository getAddressRepository() {
        return addressRepo;
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
}
