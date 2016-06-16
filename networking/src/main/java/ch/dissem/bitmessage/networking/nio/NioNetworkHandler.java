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
import ch.dissem.bitmessage.entity.MessagePayload;
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

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SERVER;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SYNC;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.utils.DebugUtils.inc;
import static ch.dissem.bitmessage.utils.ThreadFactoryBuilder.pool;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * Network handler using java.nio, resulting in less threads.
 */
public class NioNetworkHandler implements NetworkHandler, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NioNetworkHandler.class);

    private final ExecutorService pool = Executors.newCachedThreadPool(
            pool("network")
                    .lowPrio()
                    .daemon()
                    .build());

    private InternalContext ctx;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    @Override
    public Future<Void> synchronize(final InetAddress server, final int port, final MessageListener listener, long timeoutInSeconds) {
        return pool.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Set<InventoryVector> requestedObjects = new HashSet<>();
                try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {
                    channel.finishConnect();
                    channel.configureBlocking(false);
                    ConnectionInfo connection = new ConnectionInfo(ctx, SYNC,
                            new NetworkAddress.Builder().ip(server).port(port).stream(1).build(),
                            listener, new HashSet<InventoryVector>());
                    while (channel.isConnected() &&
                            (connection.getState() != ACTIVE
                                    || connection.getSendingQueue().isEmpty()
                                    || requestedObjects.isEmpty())) {
                        write(requestedObjects, channel, connection);
                        read(channel, connection);
                        Thread.sleep(10);
                    }
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
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();

            V3MessageReader reader = new V3MessageReader();
            while (reader.getMessages().isEmpty()) {
                channel.read(buffer);
                buffer.flip();
                reader.update(buffer);
            }
            NetworkMessage networkMessage = reader.getMessages().get(0);

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
        if (selector != null && selector.isOpen()) {
            throw new IllegalStateException("Network already running - you need to stop first.");
        }
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
        final Set<InventoryVector> requestedObjects = new HashSet<>();
        start("connection listener", new Runnable() {
            @Override
            public void run() {
                try {
                    serverChannel = ServerSocketChannel.open();
                    serverChannel.socket().bind(new InetSocketAddress(ctx.getPort()));
                    while (selector.isOpen() && serverChannel.isOpen()) {
                        try {
                            SocketChannel accepted = serverChannel.accept();
                            accepted.configureBlocking(false);
                            accepted.register(selector, OP_READ | OP_WRITE,
                                    new ConnectionInfo(ctx, SERVER,
                                            new NetworkAddress.Builder().address(accepted.getRemoteAddress()).stream(1).build(),
                                            listener,
                                            requestedObjects
                                    ));
                        } catch (AsynchronousCloseException ignore) {
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

        start("connection starter", new Runnable() {
            @Override
            public void run() {
                while (selector.isOpen()) {
                    List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(
                            2, ctx.getStreams());
                    for (NetworkAddress address : addresses) {
                        try {
                            SocketChannel channel = SocketChannel.open(
                                    new InetSocketAddress(address.toInetAddress(), address.getPort()));
                            channel.configureBlocking(false);
                            channel.register(selector, OP_READ | OP_WRITE,
                                    new ConnectionInfo(ctx, CLIENT,
                                            address,
                                            listener,
                                            requestedObjects
                                    ));
                        } catch (AsynchronousCloseException ignore) {
                        } catch (IOException e) {
                            LOG.error(e.getMessage(), e);
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

        start("processor", new Runnable() {
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
                                if (key.isWritable()) {
                                    write(requestedObjects, channel, connection);
                                }
                                if (key.isReadable()) {
                                    read(channel, connection);
                                }
                                if (connection.getSendingQueue().isEmpty()) {
                                    key.interestOps(OP_READ);
                                } else {
                                    key.interestOps(OP_READ | OP_WRITE);
                                }
                            }
                            keyIterator.remove();
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

    private static void write(Set<InventoryVector> requestedObjects, SocketChannel channel, ConnectionInfo connection)
            throws IOException {
        if (!connection.getSendingQueue().isEmpty()) {
            ByteBuffer buffer = connection.getOutBuffer();
            if (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            while (!buffer.hasRemaining()
                    && !connection.getSendingQueue().isEmpty()) {
                buffer.clear();
                MessagePayload payload = connection.getSendingQueue().poll();
                if (payload instanceof GetData) {
                    requestedObjects.addAll(((GetData) payload).getInventory());
                }
                new NetworkMessage(payload).write(buffer);
                buffer.flip();
                if (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        }
    }

    private static void read(SocketChannel channel, ConnectionInfo connection) throws IOException {
        ByteBuffer buffer = connection.getInBuffer();
        while (channel.read(buffer) > 0) {
            buffer.flip();
            connection.updateReader();
            buffer.compact();
        }
    }

    private void start(String threadName, Runnable runnable) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    @Override
    public void stop() {
        try {
            serverChannel.socket().close();
            for (SelectionKey key : selector.keys()) {
                key.channel().close();
            }
            selector.close();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public void offer(InventoryVector iv) {
        // TODO
    }

    @Override
    public void request(Collection<InventoryVector> inventoryVectors) {
        // TODO
    }

    @Override
    public Property getNetworkStatus() {
        TreeSet<Long> streams = new TreeSet<>();
        TreeMap<Long, Integer> incomingConnections = new TreeMap<>();
        TreeMap<Long, Integer> outgoingConnections = new TreeMap<>();

        for (SelectionKey key : selector.keys()) {
            if (key.attachment() instanceof ConnectionInfo) {
                ConnectionInfo connection = (ConnectionInfo) key.attachment();
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
                new Property("connectionManager", isRunning() ? "running" : "stopped"),
                new Property("connections", null, streamProperties),
                new Property("requestedObjects", "requestedObjects.size()") // TODO
        );
    }

    @Override
    public boolean isRunning() {
        return selector != null && selector.isOpen();
    }

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }
}
