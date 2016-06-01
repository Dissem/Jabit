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

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.InternalContext.ContextHolder;
import ch.dissem.bitmessage.entity.CustomMessage;
import ch.dissem.bitmessage.entity.GetData;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.Collections;
import ch.dissem.bitmessage.utils.Property;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SERVER;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.utils.DebugUtils.inc;
import static ch.dissem.bitmessage.utils.ThreadFactoryBuilder.pool;
import static java.util.Collections.newSetFromMap;

/**
 * Handles all the networky stuff.
 */
public class DefaultNetworkHandler implements NetworkHandler, ContextHolder {

    final Collection<Connection> connections = new ConcurrentLinkedQueue<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(
            pool("network")
                    .lowPrio()
                    .daemon()
                    .build());
    private InternalContext ctx;
    private ServerRunnable server;
    private volatile boolean running;

    final Set<InventoryVector> requestedObjects = newSetFromMap(new ConcurrentHashMap<InventoryVector, Boolean>(50_000));

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }

    @Override
    public Future<?> synchronize(InetAddress server, int port, MessageListener listener, long timeoutInSeconds) {
        try {
            Connection connection = Connection.sync(ctx, server, port, listener, timeoutInSeconds);
            Future<?> reader = pool.submit(connection.getReader());
            pool.execute(connection.getWriter());
            return reader;
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public CustomMessage send(InetAddress server, int port, CustomMessage request) {
        try (Socket socket = new Socket(server, port)) {
            socket.setSoTimeout(Connection.READ_TIMEOUT);
            new NetworkMessage(request).write(socket.getOutputStream());
            NetworkMessage networkMessage = Factory.getNetworkMessage(3, socket.getInputStream());
            if (networkMessage != null && networkMessage.getPayload() instanceof CustomMessage) {
                return (CustomMessage) networkMessage.getPayload();
            } else {
                if (networkMessage == null) {
                    throw new NodeException("No response from node " + server);
                } else {
                    throw new NodeException("Unexpected response from node " +
                            server + ": " + networkMessage.getPayload().getCommand());
                }
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public void start(final MessageListener listener) {
        if (listener == null) {
            throw new IllegalStateException("Listener must be set at start");
        }
        if (running) {
            throw new IllegalStateException("Network already running - you need to stop first.");
        }
        try {
            running = true;
            connections.clear();
            server = new ServerRunnable(ctx, this, listener);
            pool.execute(server);
            pool.execute(new ConnectionOrganizer(ctx, this, listener));
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        server.close();
        synchronized (connections) {
            running = false;
            for (Connection c : connections) {
                c.disconnect();
            }
        }
        requestedObjects.clear();
    }

    void startConnection(Connection c) {
        if (!running) return;

        synchronized (connections) {
            if (!running) return;

            // prevent connecting twice to the same node
            if (connections.contains(c)) {
                return;
            }
            connections.add(c);
        }
        pool.execute(c.getReader());
        pool.execute(c.getWriter());
    }

    @Override
    public void offer(final InventoryVector iv) {
        List<Connection> target = new LinkedList<>();
        for (Connection connection : connections) {
            if (connection.getState() == ACTIVE && !connection.knowsOf(iv)) {
                target.add(connection);
            }
        }
        List<Connection> randomSubset = Collections.selectRandom(NETWORK_MAGIC_NUMBER, target);
        for (Connection connection : randomSubset) {
            connection.offer(iv);
        }
    }

    @Override
    public Property getNetworkStatus() {
        TreeSet<Long> streams = new TreeSet<>();
        TreeMap<Long, Integer> incomingConnections = new TreeMap<>();
        TreeMap<Long, Integer> outgoingConnections = new TreeMap<>();

        for (Connection connection : connections) {
            if (connection.getState() == ACTIVE) {
                long stream = connection.getNode().getStream();
                streams.add(stream);
                if (connection.getMode() == SERVER) {
                    inc(incomingConnections, stream);
                } else {
                    inc(outgoingConnections, stream);
                }
            }
        }
        Property[] streamProperties = new Property[streams.size()];
        int i = 0;
        for (Long stream : streams) {
            int incoming = incomingConnections.containsKey(stream) ? incomingConnections.get(stream) : 0;
            int outgoing = outgoingConnections.containsKey(stream) ? outgoingConnections.get(stream) : 0;
            streamProperties[i] = new Property("stream " + stream,
                    null, new Property("nodes", incoming + outgoing),
                    new Property("incoming", incoming),
                    new Property("outgoing", outgoing)
            );
            i++;
        }
        return new Property("network", null,
                new Property("connectionManager", running ? "running" : "stopped"),
                new Property("connections", null, streamProperties),
                new Property("requestedObjects", requestedObjects.size())
        );
    }

    @Override
    public void request(Collection<InventoryVector> inventoryVectors) {
        if (!running || inventoryVectors.isEmpty()) return;

        Map<Connection, List<InventoryVector>> distribution = new HashMap<>();
        for (Connection connection : connections) {
            if (connection.getState() == ACTIVE) {
                distribution.put(connection, new LinkedList<InventoryVector>());
            }
        }
        Iterator<InventoryVector> iterator = inventoryVectors.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        InventoryVector next = iterator.next();
        Connection previous = null;
        do {
            for (Connection connection : distribution.keySet()) {
                if (connection == previous) {
                    next = iterator.next();
                }
                if (connection.knowsOf(next)) {
                    List<InventoryVector> ivs = distribution.get(connection);
                    if (ivs.size() == GetData.MAX_INVENTORY_SIZE) {
                        connection.send(new GetData.Builder().inventory(ivs).build());
                        ivs.clear();
                    }
                    ivs.add(next);
                    iterator.remove();

                    if (iterator.hasNext()) {
                        next = iterator.next();
                        previous = connection;
                    } else {
                        break;
                    }
                }
            }
        } while (iterator.hasNext());

        for (Connection connection : distribution.keySet()) {
            List<InventoryVector> ivs = distribution.get(connection);
            if (!ivs.isEmpty()) {
                connection.send(new GetData.Builder().inventory(ivs).build());
            }
        }
    }
}
