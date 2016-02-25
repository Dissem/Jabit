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
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.Collections;
import ch.dissem.bitmessage.utils.Property;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.networking.Connection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.Connection.Mode.SERVER;
import static ch.dissem.bitmessage.networking.Connection.State.ACTIVE;
import static ch.dissem.bitmessage.utils.DebugUtils.inc;
import static java.util.Collections.newSetFromMap;

/**
 * Handles all the networky stuff.
 */
public class DefaultNetworkHandler implements NetworkHandler, ContextHolder {
    private final static Logger LOG = LoggerFactory.getLogger(DefaultNetworkHandler.class);

    public final static int NETWORK_MAGIC_NUMBER = 8;

    private final Collection<Connection> connections = new ConcurrentLinkedQueue<>();
    private final ExecutorService pool;
    private InternalContext ctx;
    private ServerSocket serverSocket;
    private volatile boolean running;

    private Set<InventoryVector> requestedObjects = newSetFromMap(new ConcurrentHashMap<InventoryVector, Boolean>(50_000));

    public DefaultNetworkHandler() {
        pool = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });
    }

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
            serverSocket = new ServerSocket(ctx.getPort());
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    while (!serverSocket.isClosed()) {
                        try {
                            Socket socket = serverSocket.accept();
                            socket.setSoTimeout(Connection.READ_TIMEOUT);
                            startConnection(new Connection(ctx, SERVER, socket, listener, requestedObjects));
                        } catch (IOException e) {
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                }
            });
            pool.execute(new Runnable() {
                public Connection initialConnection;

                @Override
                public void run() {
                    try {
                        while (running) {
                            try {
                                int active = 0;
                                long now = UnixTime.now();
                                synchronized (connections) {
                                    int diff = connections.size() - ctx.getConnectionLimit();
                                    if (diff > 0) {
                                        for (Connection c : connections) {
                                            c.disconnect();
                                            diff--;
                                            if (diff == 0) break;
                                        }
                                    }
                                    boolean forcedDisconnect = false;
                                    for (Iterator<Connection> iterator = connections.iterator(); iterator.hasNext(); ) {
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
                                }
                                if (active < NETWORK_MAGIC_NUMBER) {
                                    List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(
                                            NETWORK_MAGIC_NUMBER - active, ctx.getStreams());
                                    boolean first = active == 0 && initialConnection == null;
                                    for (NetworkAddress address : addresses) {
                                        Connection c = new Connection(ctx, CLIENT, address, listener, requestedObjects);
                                        if (first) {
                                            initialConnection = c;
                                            first = false;
                                        }
                                        startConnection(c);
                                    }
                                    Thread.sleep(10000);
                                } else if (initialConnection != null) {
                                    initialConnection.disconnect();
                                    initialConnection = null;
                                    Thread.sleep(10000);
                                } else {
                                    Thread.sleep(30000);
                                }
                            } catch (InterruptedException e) {
                                running = false;
                            } catch (Exception e) {
                                LOG.error("Error in connection manager. Ignored.", e);
                            }
                        }
                    } finally {
                        LOG.debug("Connection manager shutting down.");
                        running = false;
                    }
                }
            });
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
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
        }
        synchronized (connections) {
            for (Connection c : connections) {
                c.disconnect();
            }
        }
        requestedObjects.clear();
    }

    private void startConnection(Connection c) {
        synchronized (connections) {
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
        synchronized (connections) {
            for (Connection connection : connections) {
                if (connection.getState() == ACTIVE && !connection.knowsOf(iv)) {
                    target.add(connection);
                }
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

        synchronized (connections) {
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

    void request(Set<InventoryVector> inventoryVectors) {
        if (!running || inventoryVectors.isEmpty()) return;
        synchronized (connections) {
            Map<Connection, List<InventoryVector>> distribution = new HashMap<>();
            for (Connection connection : connections) {
                if (connection.getState() == ACTIVE) {
                    distribution.put(connection, new LinkedList<InventoryVector>());
                }
            }
            Iterator<InventoryVector> iterator = inventoryVectors.iterator();
            InventoryVector next;
            if (iterator.hasNext()) {
                next = iterator.next();
            } else {
                return;
            }
            boolean firstRound = true;
            while (firstRound || iterator.hasNext()) {
                if (!firstRound) {
                    next = iterator.next();
                    firstRound = true;
                } else {
                    firstRound = false;
                }
                for (Connection connection : distribution.keySet()) {
                    if (connection.knowsOf(next)) {
                        List<InventoryVector> ivs = distribution.get(connection);
                        if (ivs.size() == 50_000) {
                            connection.send(new GetData.Builder().inventory(ivs).build());
                            ivs.clear();
                        }
                        ivs.add(next);
                        iterator.remove();

                        if (iterator.hasNext()) {
                            next = iterator.next();
                            firstRound = true;
                        } else {
                            firstRound = false;
                            break;
                        }
                    }
                }
            }
            for (Connection connection : distribution.keySet()) {
                List<InventoryVector> ivs = distribution.get(connection);
                if (!ivs.isEmpty()) {
                    connection.send(new GetData.Builder().inventory(ivs).build());
                }
            }
        }
    }
}
