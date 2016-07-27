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

package ch.dissem.bitmessage.networking.nio;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.CustomMessage;
import ch.dissem.bitmessage.entity.GetData;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.factory.V3MessageReader;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.*;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.DISCONNECTED;
import static ch.dissem.bitmessage.utils.Collections.selectRandom;
import static ch.dissem.bitmessage.utils.DebugUtils.inc;
import static ch.dissem.bitmessage.utils.ThreadFactoryBuilder.pool;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.Collections.synchronizedMap;

/**
 * Network handler using java.nio, resulting in less threads.
 */
public class NioNetworkHandler implements NetworkHandler, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NioNetworkHandler.class);

    private final ExecutorService threadPool = Executors.newCachedThreadPool(
        pool("network")
            .lowPrio()
            .daemon()
            .build());

    private InternalContext ctx;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Map<ConnectionInfo, SelectionKey> connections = new ConcurrentHashMap<>();
    private int requestedObjectsCount;

    private Thread starter;

    @Override
    public Future<Void> synchronize(final InetAddress server, final int port, final MessageListener listener, final long timeoutInSeconds) {
        return threadPool.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {
                    channel.configureBlocking(false);
                    ConnectionInfo connection = new ConnectionInfo(ctx, SYNC,
                        new NetworkAddress.Builder().ip(server).port(port).stream(1).build(),
                        listener, new HashSet<InventoryVector>(), timeoutInSeconds);
                    while (channel.isConnected() && !connection.isSyncFinished()) {
                        write(channel, connection);
                        read(channel, connection);
                        Thread.sleep(10);
                    }
                    LOG.info("Synchronization finished");
                }
                return null;
            }
        });
    }

    @Override
    public CustomMessage send(InetAddress server, int port, CustomMessage request) {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {
            channel.configureBlocking(true);
            ByteBuffer buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
            new NetworkMessage(request).write(buffer);
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();

            V3MessageReader reader = new V3MessageReader();
            while (channel.isConnected() && reader.getMessages().isEmpty()) {
                if (channel.read(reader.getActiveBuffer()) > 0) {
                    reader.update();
                } else {
                    throw new NodeException("No response from node " + server);
                }
            }
            NetworkMessage networkMessage;
            if (reader.getMessages().isEmpty()) {
                throw new NodeException("No response from node " + server);
            } else {
                networkMessage = reader.getMessages().get(0);
            }

            if (networkMessage != null && networkMessage.getPayload() instanceof CustomMessage) {
                return (CustomMessage) networkMessage.getPayload();
            } else {
                if (networkMessage == null || networkMessage.getPayload() == null) {
                    throw new NodeException("Empty response from node " + server);
                } else {
                    throw new NodeException("Unexpected response from node " + server + ": "
                        + networkMessage.getPayload().getClass());
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
        if (selector != null && selector.isOpen()) {
            throw new IllegalStateException("Network already running - you need to stop first.");
        }
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
        final Set<InventoryVector> requestedObjects = new HashSet<>();
        thread("connection listener", new Runnable() {
            @Override
            public void run() {
                try {
                    serverChannel = ServerSocketChannel.open();
                    serverChannel.socket().bind(new InetSocketAddress(ctx.getPort()));
                    while (selector.isOpen() && serverChannel.isOpen()) {
                        try {
                            SocketChannel accepted = serverChannel.accept();
                            accepted.configureBlocking(false);
                            ConnectionInfo connection = new ConnectionInfo(ctx, SERVER,
                                new NetworkAddress.Builder().address(accepted.getRemoteAddress()).stream(1).build(),
                                listener,
                                requestedObjects, 0
                            );
                            connections.put(
                                connection,
                                accepted.register(selector, OP_READ | OP_WRITE, connection)
                            );
                        } catch (AsynchronousCloseException ignore) {
                            LOG.trace(ignore.getMessage());
                        } catch (IOException e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                } catch (ClosedSelectorException | AsynchronousCloseException ignore) {
                } catch (IOException e) {
                    throw new ApplicationException(e);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        });

        starter = thread("connection starter", new Runnable() {
            @Override
            public void run() {
                while (selector.isOpen()) {
                    int missing = NETWORK_MAGIC_NUMBER;
                    for (ConnectionInfo connectionInfo : connections.keySet()) {
                        if (connectionInfo.getState() == ACTIVE) {
                            missing--;
                            if (missing == 0) break;
                        }
                    }
                    if (missing > 0) {
                        List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(missing, ctx.getStreams());
                        for (NetworkAddress address : addresses) {
                            try {
                                SocketChannel channel = SocketChannel.open();
                                channel.configureBlocking(false);
                                channel.connect(new InetSocketAddress(address.toInetAddress(), address.getPort()));
                                channel.finishConnect();
                                ConnectionInfo connection = new ConnectionInfo(ctx, CLIENT,
                                    address,
                                    listener,
                                    requestedObjects, 0
                                );
                                connections.put(
                                    connection,
                                    channel.register(selector, OP_READ | OP_WRITE, connection)
                                );
                            } catch (AsynchronousCloseException ignore) {
                            } catch (IOException e) {
                                LOG.error(e.getMessage(), e);
                            }
                        }
                    }

                    Iterator<Map.Entry<ConnectionInfo, SelectionKey>> it = connections.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<ConnectionInfo, SelectionKey> e = it.next();
                        if (!e.getValue().isValid() || e.getKey().isExpired()) {
                            try {
                                e.getValue().channel().close();
                            } catch (Exception ignore) {
                            }
                            e.getValue().cancel();
                            e.getValue().attach(null);
                            it.remove();
                            e.getKey().disconnect();
                        }
                    }
                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });

        thread("processor", new Runnable() {
            @Override
            public void run() {
                try {
                    while (selector.isOpen()) {
                        selector.select(1000);
                        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            if (key.attachment() instanceof ConnectionInfo) {
                                SocketChannel channel = (SocketChannel) key.channel();
                                ConnectionInfo connection = (ConnectionInfo) key.attachment();
                                try {
                                    if (key.isWritable()) {
                                        write(channel, connection);
                                    }
                                    if (key.isReadable()) {
                                        read(channel, connection);
                                    }
                                    if (connection.getSendingQueue().isEmpty()) {
                                        if (connection.getState() == DISCONNECTED) {
                                            key.interestOps(0);
                                            key.channel().close();
                                        } else {
                                            key.interestOps(OP_READ);
                                        }
                                    } else {
                                        key.interestOps(OP_READ | OP_WRITE);
                                    }
                                } catch (NodeException | IOException e) {
                                    connection.disconnect();
                                }
                                if (connection.getState() == DISCONNECTED) {
                                    connections.remove(connection);
                                }
                            }
                            keyIterator.remove();
                            requestedObjectsCount = requestedObjects.size();
                        }
                        for (Map.Entry<ConnectionInfo, SelectionKey> e : connections.entrySet()) {
                            if (e.getValue().isValid() && (e.getValue().interestOps() & OP_WRITE) == 0) {
                                if (!e.getKey().getSendingQueue().isEmpty()) {
                                    e.getValue().interestOps(OP_READ | OP_WRITE);
                                }
                            }
                        }
                    }
                    selector.close();
                } catch (ClosedSelectorException ignore) {
                } catch (IOException e) {
                    throw new ApplicationException(e);
                }
            }
        });
    }

    private static void write(SocketChannel channel, ConnectionInfo connection)
        throws IOException {
        writeBuffer(connection.getOutBuffers(), channel);

        connection.updateWriter();

        writeBuffer(connection.getOutBuffers(), channel);
        connection.cleanupBuffers();
    }

    private static void writeBuffer(ByteBuffer[] buffers, SocketChannel channel) throws IOException {
        if (buffers[1] == null) {
            if (buffers[0].hasRemaining()) {
                channel.write(buffers[0]);
            }
        } else if (buffers[1].hasRemaining() || buffers[0].hasRemaining()) {
            channel.write(buffers);
        }
    }

    private static void read(SocketChannel channel, ConnectionInfo connection) throws IOException {
        while (channel.read(connection.getInBuffer()) > 0) {
            connection.updateReader();
        }
        connection.updateSyncStatus();
    }

    private Thread thread(String threadName, Runnable runnable) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return thread;
    }

    @Override
    public void stop() {
        try {
            serverChannel.socket().close();
            selector.close();
            for (SelectionKey selectionKey : connections.values()) {
                selectionKey.channel().close();
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public void offer(InventoryVector iv) {
        List<ConnectionInfo> target = new LinkedList<>();
        for (ConnectionInfo connection : connections.keySet()) {
            if (connection.getState() == ACTIVE && !connection.knowsOf(iv)) {
                target.add(connection);
            }
        }
        List<ConnectionInfo> randomSubset = selectRandom(NETWORK_MAGIC_NUMBER, target);
        for (ConnectionInfo connection : randomSubset) {
            connection.offer(iv);
        }
    }

    @Override
    public void request(Collection<InventoryVector> inventoryVectors) {
        if (!isRunning()) return;
        Iterator<InventoryVector> iterator = inventoryVectors.iterator();
        if (!iterator.hasNext()) {
            return;
        }

        Map<ConnectionInfo, List<InventoryVector>> distribution = new HashMap<>();
        for (ConnectionInfo connection : connections.keySet()) {
            if (connection.getState() == ACTIVE) {
                distribution.put(connection, new LinkedList<InventoryVector>());
            }
        }
        InventoryVector next = iterator.next();
        ConnectionInfo previous = null;
        do {
            for (ConnectionInfo connection : distribution.keySet()) {
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

        for (ConnectionInfo connection : distribution.keySet()) {
            List<InventoryVector> ivs = distribution.get(connection);
            if (!ivs.isEmpty()) {
                connection.send(new GetData.Builder().inventory(ivs).build());
            }
        }
    }

    @Override
    public Property getNetworkStatus() {
        TreeSet<Long> streams = new TreeSet<>();
        TreeMap<Long, Integer> incomingConnections = new TreeMap<>();
        TreeMap<Long, Integer> outgoingConnections = new TreeMap<>();

        for (ConnectionInfo connection : connections.keySet()) {
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
            new Property("connectionManager", isRunning() ? "running" : "stopped"),
            new Property("connections", null, streamProperties),
            new Property("requestedObjects", requestedObjectsCount)
        );
    }

    @Override
    public boolean isRunning() {
        return selector != null && selector.isOpen() && starter.isAlive();
    }

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }
}
