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

import ch.dissem.bitmessage.Context;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ch.dissem.bitmessage.networking.Connection.State.*;

/**
 * Handles all the networky stuff.
 */
public class NetworkNode implements NetworkHandler {
    private final static Logger LOG = LoggerFactory.getLogger(NetworkNode.class);
    private final ExecutorService pool;
    private final List<Connection> connections = new LinkedList<>();
    private MessageListener listener;

    public NetworkNode() {
        pool = Executors.newCachedThreadPool();
    }

    @Override
    public void setListener(final MessageListener listener) {
        if (this.listener != null) {
            throw new IllegalStateException("Listener can only be set once");
        }
        this.listener = listener;
    }

    @Override
    public void start() {
        final Context ctx = Context.getInstance();
        if (listener == null) {
            throw new IllegalStateException("Listener must be set at start");
        }
        try {
            final ServerSocket serverSocket = new ServerSocket(Context.getInstance().getPort());
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(20000);
                        startConnection(new Connection(SERVER, socket, listener));
                    } catch (IOException e) {
                        LOG.debug(e.getMessage(), e);
                    }
                }
            });
            Thread connectionManager = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        synchronized (connections) {
                            for (Iterator<Connection> iterator = connections.iterator(); iterator.hasNext(); ) {
                                Connection c = iterator.next();
                                if (c.getState() == DISCONNECTED) {
                                    // Remove the current element from the iterator and the list.
                                    iterator.remove();
                                }
                            }
                        }
                        if (connections.size() < 1) {
                            List<NetworkAddress> addresses = ctx.getAddressRepository().getKnownAddresses(8, ctx.getStreams());
                            for (NetworkAddress address : addresses) {
                                try {
                                    startConnection(new Connection(CLIENT, new Socket(address.toInetAddress(), address.getPort()), listener));
                                } catch (IOException e) {
                                    LOG.debug(e.getMessage(), e);
                                }
                            }
                            // FIXME: prevent connecting twice to the same node
                        }
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException e) {
                            LOG.debug(e.getMessage(), e);
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }, "connection-manager");
            connectionManager.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startConnection(Connection c) {
        synchronized (connections) {
            connections.add(c);
        }
        pool.execute(c);
    }

    @Override
    public void send(final ObjectPayload payload) {
        // TODO: sendingQueue.add(message);
    }
}
