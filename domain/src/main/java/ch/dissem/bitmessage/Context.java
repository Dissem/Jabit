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
import ch.dissem.bitmessage.ports.NetworkMessageReceiver;
import ch.dissem.bitmessage.ports.NetworkMessageSender;

/**
 * Created by chris on 05.04.15.
 */
public class Context {
    public static final int CURRENT_VERSION = 3;

    private static Context instance;

    private Inventory inventory;
    private AddressRepository addressRepo;
    private NetworkMessageSender sender;
    private NetworkMessageReceiver receiver;

    private Context(Inventory inventory, AddressRepository addressRepo,
                    NetworkMessageSender sender, NetworkMessageReceiver receiver) {
        this.inventory = inventory;
        this.addressRepo = addressRepo;
        this.sender = sender;
        this.receiver = receiver;
    }

    public static void init(Inventory inventory, AddressRepository addressRepository, NetworkMessageSender sender, NetworkMessageReceiver receiver) {
        instance = new Context(inventory, addressRepository, sender, receiver);
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
}
