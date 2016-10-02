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

package ch.dissem.bitmessage.networking;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.DefaultNetworkHandler.NETWORK_MAGIC_NUMBER;

/**
 * @author Christian Basler
 */
public class ConnectionOrganizer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionOrganizer.class);

    private final InternalContext ctx;
    private final DefaultNetworkHandler networkHandler;
    private final NetworkHandler.MessageListener listener;

    private Connection initialConnection;

    public ConnectionOrganizer(InternalContext ctx,
                               DefaultNetworkHandler networkHandler) {
        this.ctx = ctx;
        this.networkHandler = networkHandler;
        this.listener = ctx.getNetworkListener();
    }

    @Override
    public void run() {
        try {
            while (networkHandler.isRunning()) {
                try {
                    int active = 0;
                    long now = UnixTime.now();

                    int diff = networkHandler.connections.size() - ctx.getConnectionLimit();
                    if (diff > 0) {
                        for (Connection c : networkHandler.connections) {
                            c.disconnect();
                            diff--;
                            if (diff == 0) break;
                        }
                    }
                    boolean forcedDisconnect = false;
                    for (Iterator<Connection> iterator = networkHandler.connections.iterator(); iterator.hasNext(); ) {
                        Connection c = iterator.next();
                        // Just in case they were all created at the same time, don't disconnect
                        // all at once.
                        if (!forcedDisconnect && now - c.getStartTime() > ctx.getConnectionTTL()) {
                            c.disconnect();
                            forcedDisconnect = true;
                        }
                        switch (c.getState()) {
                            case DISCONNECTED:
                                iterator.remove();
                                break;
                            case ACTIVE:
                                active++;
                                break;
                            default:
                                // nothing to do
                        }
                    }

                    if (active < NETWORK_MAGIC_NUMBER) {
                        List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(
                                NETWORK_MAGIC_NUMBER - active, ctx.getStreams());
                        boolean first = active == 0 && initialConnection == null;
                        for (NetworkAddress address : addresses) {
                            Connection c = new Connection(ctx, CLIENT, address, networkHandler.requestedObjects);
                            if (first) {
                                initialConnection = c;
                                first = false;
                            }
                            networkHandler.startConnection(c);
                        }
                        Thread.sleep(10000);
                    } else if (initialConnection == null) {
                        Thread.sleep(30000);
                    } else {
                        initialConnection.disconnect();
                        initialConnection = null;
                        Thread.sleep(10000);
                    }
                } catch (InterruptedException e) {
                    networkHandler.stop();
                } catch (Exception e) {
                    LOG.error("Error in connection manager. Ignored.", e);
                }
            }
        } finally {
            LOG.debug("Connection manager shutting down.");
            networkHandler.stop();
        }
    }
}
