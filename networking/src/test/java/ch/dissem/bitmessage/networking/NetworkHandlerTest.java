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

package ch.dissem.bitmessage.networking;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.ports.AddressRepository;
import ch.dissem.bitmessage.ports.MessageRepository;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.security.bc.BouncySecurity;
import ch.dissem.bitmessage.utils.Property;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * FIXME: there really should be sensible tests for the network handler
 */
public class NetworkHandlerTest {
    private static NetworkAddress localhost = new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(6001).build();

    private static TestInventory peerInventory;
    private static TestInventory nodeInventory;

    private static BitmessageContext node;
    private static NetworkHandler networkHandler;

    @BeforeClass
    public static void setUp() {
        peerInventory = new TestInventory();
        BitmessageContext peer = new BitmessageContext.Builder()
                .addressRepo(Mockito.mock(AddressRepository.class))
                .inventory(peerInventory)
                .messageRepo(Mockito.mock(MessageRepository.class))
                .port(6001)
                .nodeRegistry(new TestNodeRegistry())
                .networkHandler(new DefaultNetworkHandler())
                .security(new BouncySecurity())
                .build();
        peer.startup(Mockito.mock(BitmessageContext.Listener.class));

        nodeInventory = new TestInventory();
        networkHandler = new DefaultNetworkHandler();
        node = new BitmessageContext.Builder()
                .addressRepo(Mockito.mock(AddressRepository.class))
                .inventory(nodeInventory)
                .messageRepo(Mockito.mock(MessageRepository.class))
                .port(6002)
                .nodeRegistry(new TestNodeRegistry(localhost))
                .networkHandler(networkHandler)
                .security(new BouncySecurity())
                .build();
    }

    @Test(timeout = 20_000)
    public void ensureNodesAreConnecting() {
        try {
            node.startup(Mockito.mock(BitmessageContext.Listener.class));
            Property status;
            do {
                Thread.yield();
                status = node.status().getProperty("network").getProperty("connections").getProperty("stream 0");
            } while (status == null);
            assertEquals(1, status.getProperty("outgoing").getValue());
        } finally {
            shutdown(node);
        }
    }

    @Test(timeout = 5_000)
    public void ensureObjectsAreSynchronizedIfBothHaveObjects() throws Exception {
        peerInventory.init(
                "V4Pubkey.payload",
                "V5Broadcast.payload"
        );

        nodeInventory.init(
                "V1Msg.payload"
        );

        Thread t = networkHandler.synchronize(InetAddress.getLocalHost(), 6001,
                mock(NetworkHandler.MessageListener.class),
                10);
        t.join();
        assertEquals(3, nodeInventory.getInventory().size());
        assertEquals(3, peerInventory.getInventory().size());
    }

    @Test(timeout = 5_000)
    public void ensureObjectsAreSynchronizedIfOnlyPeerHasObjects() throws Exception {
        peerInventory.init(
                "V4Pubkey.payload",
                "V5Broadcast.payload"
        );

        nodeInventory.init();

        Thread t = networkHandler.synchronize(InetAddress.getLocalHost(), 6001,
                mock(NetworkHandler.MessageListener.class),
                10);
        t.join();
        assertEquals(2, nodeInventory.getInventory().size());
        assertEquals(2, peerInventory.getInventory().size());
    }

    @Test(timeout = 5_000)
    public void ensureObjectsAreSynchronizedIfOnlyNodeHasObjects() throws Exception {
        peerInventory.init();

        nodeInventory.init(
                "V1Msg.payload"
        );

        Thread t = networkHandler.synchronize(InetAddress.getLocalHost(), 6001,
                mock(NetworkHandler.MessageListener.class),
                10);
        t.join();
        assertEquals(1, nodeInventory.getInventory().size());
        assertEquals(1, peerInventory.getInventory().size());
    }

    private void shutdown(BitmessageContext node) {
        node.shutdown();
        do {
            Thread.yield();
        } while (node.isRunning());
    }
}
