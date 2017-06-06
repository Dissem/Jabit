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
import ch.dissem.bitmessage.networking.AbstractConnection;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.DebugUtils;
import ch.dissem.bitmessage.utils.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.constants.Network.HEADER_SIZE;
import static ch.dissem.bitmessage.constants.Network.NETWORK_MAGIC_NUMBER;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SERVER;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SYNC;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.DISCONNECTED;
import static ch.dissem.bitmessage.utils.Collections.selectRandom;
import static ch.dissem.bitmessage.utils.ThreadFactoryBuilder.pool;
import static java.nio.channels.SelectionKey.*;

/**
 * Network handler using java.nio, resulting in less threads.
 */
public class NioNetworkHandler implements NetworkHandler, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NioNetworkHandler.class);
    private static final long REQUESTED_OBJECTS_MAX_TIME = 2 * 60_000; // 2 minutes
    private static final Long DELAYED = Long.MIN_VALUE;

    private final ExecutorService threadPool = Executors.newCachedThreadPool(
        pool("network")
            .lowPrio()
            .daemon()
            .build());

    private InternalContext ctx;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Queue<NetworkAddress> connectionQueue = new ConcurrentLinkedQueue<>();
    private Map<ConnectionInfo, SelectionKey> connections = new ConcurrentHashMap<>();
    private final Map<InventoryVector, Long> requestedObjects = new ConcurrentHashMap<>(10_000);

    private Thread starter;

    @Override
    public Future<Void> synchronize(final InetAddress server, final int port, final long timeoutInSeconds) {
        return threadPool.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {
                    channel.configureBlocking(false);
                    ConnectionInfo connection = new ConnectionInfo(ctx, SYNC,
                        new NetworkAddress.Builder().ip(server).port(port).stream(1).build(),
                        new HashMap<InventoryVector, Long>(), timeoutInSeconds);
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
            ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
            ByteBuffer payloadBuffer = new NetworkMessage(request).writeHeaderAndGetPayloadBuffer(headerBuffer);
            headerBuffer.flip();
            while (headerBuffer.hasRemaining()) {
                channel.write(headerBuffer);
            }
            while (payloadBuffer.hasRemaining()) {
                channel.write(payloadBuffer);
            }

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
    public void start() {
        if (selector != null && selector.isOpen()) {
            throw new IllegalStateException("Network already running - you need to stop first.");
        }
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
        requestedObjects.clear();

        starter = thread("connection manager", new Runnable() {
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
                        List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(100, ctx.getStreams());
                        addresses = selectRandom(missing, addresses);
                        for (NetworkAddress address : addresses) {
                            if (!isConnectedTo(address)) {
                                connectionQueue.offer(address);
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
                            e.getKey().disconnect();
                            it.remove();
                        }
                    }

                    // The list 'requested objects' helps to prevent downloading an object
                    // twice. From time to time there is an error though, and an object is
                    // never downloaded. To prevent a large list of failed objects and give
                    // them a chance to get downloaded again, we will attempt to download an
                    // object from another node after some time out.
                    long timedOut = System.currentTimeMillis() - REQUESTED_OBJECTS_MAX_TIME;
                    List<InventoryVector> delayed = new LinkedList<>();
                    Iterator<Map.Entry<InventoryVector, Long>> iterator = requestedObjects.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<InventoryVector, Long> e = iterator.next();
                        //noinspection NumberEquality
                        if (e.getValue() == DELAYED) {
                            iterator.remove();
                        } else if (e.getValue() < timedOut) {
                            delayed.add(e.getKey());
                            e.setValue(DELAYED);
                        }
                    }
                    request(delayed);

                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });

        thread("selector worker", new Runnable() {
            @Override
            public void run() {
                try {
                    serverChannel = ServerSocketChannel.open();
                    serverChannel.configureBlocking(false);
                    serverChannel.socket().bind(new InetSocketAddress(ctx.getPort()));
                    serverChannel.register(selector, OP_ACCEPT, null);

                    while (selector.isOpen()) {
                        selector.select(1000);
                        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            keyIterator.remove();
                            if (key.attachment() == null) {
                                try {
                                    if (key.isAcceptable()) {
                                        // handle accept
                                        try {
                                            SocketChannel accepted = ((ServerSocketChannel) key.channel()).accept();
                                            accepted.configureBlocking(false);
                                            ConnectionInfo connection = new ConnectionInfo(ctx, SERVER,
                                                new NetworkAddress.Builder()
                                                    .ip(accepted.socket().getInetAddress())
                                                    .port(accepted.socket().getPort())
                                                    .stream(1)
                                                    .build(),
                                                requestedObjects, 0
                                            );
                                            connections.put(
                                                connection,
                                                accepted.register(selector, OP_READ | OP_WRITE, connection)
                                            );
                                        } catch (AsynchronousCloseException e) {
                                            LOG.trace(e.getMessage());
                                        } catch (IOException e) {
                                            LOG.error(e.getMessage(), e);
                                        }
                                    }
                                } catch (CancelledKeyException e) {
                                    LOG.debug(e.getMessage(), e);
                                }
                            } else {
                                // handle read/write
                                SocketChannel channel = (SocketChannel) key.channel();
                                ConnectionInfo connection = (ConnectionInfo) key.attachment();
                                try {
                                    if (key.isConnectable()) {
                                        if (!channel.finishConnect()) {
                                            continue;
                                        }
                                    }
                                    if (key.isWritable()) {
                                        write(channel, connection);
                                    }
                                    if (key.isReadable()) {
                                        read(channel, connection);
                                    }
                                    if (connection.getState() == DISCONNECTED) {
                                        key.interestOps(0);
                                        channel.close();
                                    } else if (connection.isWritePending()) {
                                        key.interestOps(OP_READ | OP_WRITE);
                                    } else {
                                        key.interestOps(OP_READ);
                                    }
                                } catch (CancelledKeyException | NodeException | IOException e) {
                                    connection.disconnect();
                                }
                            }
                        }
                        // set interest ops
                        for (Map.Entry<ConnectionInfo, SelectionKey> e : connections.entrySet()) {
                            try {
                                if (e.getValue().isValid()
                                    && (e.getValue().interestOps() & OP_WRITE) == 0
                                    && (e.getValue().interestOps() & OP_CONNECT) == 0
                                    && !e.getKey().getSendingQueue().isEmpty()) {
                                    e.getValue().interestOps(OP_READ | OP_WRITE);
                                }
                            } catch (CancelledKeyException x) {
                                e.getKey().disconnect();
                            }
                        }
                        // start new connections
                        if (!connectionQueue.isEmpty()) {
                            NetworkAddress address = connectionQueue.poll();
                            try {
                                SocketChannel channel = SocketChannel.open();
                                channel.configureBlocking(false);
                                channel.connect(new InetSocketAddress(address.toInetAddress(), address.getPort()));
                                ConnectionInfo connection = new ConnectionInfo(ctx, CLIENT,
                                    address,
                                    requestedObjects, 0
                                );
                                connections.put(
                                    connection,
                                    channel.register(selector, OP_CONNECT, connection)
                                );
                            } catch (NoRouteToHostException ignore) {
                                // We'll try to connect to many offline nodes, so
                                // this is expected to happen quite a lot.
                            } catch (AsynchronousCloseException e) {
                                // The exception is expected if the network is being
                                // shut down, as we actually do asynchronously close
                                // the connections.
                                if (isRunning()) {
                                    LOG.error(e.getMessage(), e);
                                }
                            } catch (IOException e) {
                                LOG.error(e.getMessage(), e);
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
        if (channel.read(connection.getInBuffer()) > 0) {
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
        if (!isRunning()) {
            requestedObjects.clear();
            return;
        }
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
        if (distribution.isEmpty()) {
            return;
        }
        InventoryVector next = iterator.next();
        ConnectionInfo previous = null;
        do {
            for (ConnectionInfo connection : distribution.keySet()) {
                if (connection == previous || previous == null) {
                    if (iterator.hasNext()) {
                        previous = connection;
                        next = iterator.next();
                    } else {
                        break;
                    }
                }
                if (connection.knowsOf(next) && !connection.requested(next)) {
                    List<InventoryVector> ivs = distribution.get(connection);
                    if (ivs.size() == GetData.MAX_INVENTORY_SIZE) {
                        connection.send(new GetData(ivs));
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

        // remove objects nobody knows of
        for (InventoryVector iv : inventoryVectors) {
            requestedObjects.remove(iv);
        }

        for (ConnectionInfo connection : distribution.keySet()) {
            List<InventoryVector> ivs = distribution.get(connection);
            if (!ivs.isEmpty()) {
                connection.send(new GetData(ivs));
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
                for (long stream : connection.getStreams()) {
                    streams.add(stream);
                    if (connection.getMode() == SERVER) {
                        DebugUtils.inc(incomingConnections, stream);
                    } else {
                        DebugUtils.inc(outgoingConnections, stream);
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
            new Property("connectionManager", isRunning() ? "running" : "stopped"),
            new Property("connections", streamProperties),
            new Property("requestedObjects", requestedObjects.size())
        );
    }

    private boolean isConnectedTo(NetworkAddress address) {
        for (ConnectionInfo c : connections.keySet()) {
            if (c.getNode().equals(address)) {
                return true;
            }
        }
        return false;
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
