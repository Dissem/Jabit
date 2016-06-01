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
import java.util.concurrent.Future;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SERVER;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.utils.DebugUtils.inc;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * Network handler using java.nio, resulting in less threads.
 */
public class NioNetworkHandler implements NetworkHandler, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NioNetworkHandler.class);

    private InternalContext ctx;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    @Override
    public Future<?> synchronize(InetAddress server, int port, MessageListener listener, long timeoutInSeconds) {
        return null;
    }

    @Override
    public CustomMessage send(InetAddress server, int port, CustomMessage request) {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {
            channel.configureBlocking(true);
            ByteBuffer buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
            new NetworkMessage(request).write(buffer);
            channel.write(buffer);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverChannel = ServerSocketChannel.open();
                    serverChannel.bind(new InetSocketAddress(ctx.getPort()));

                    SocketChannel accepted = serverChannel.accept();
                    accepted.configureBlocking(false);
                    // FIXME: apparently it isn't good practice to generally listen for OP_WRITE
                    accepted.register(selector, OP_READ | OP_WRITE).attach(
                            new ConnectionInfo(ctx, SERVER,
                                    new NetworkAddress.Builder().address(accepted.getRemoteAddress()).stream(1).build(),
                                    listener,
                                    requestedObjects
                            ));
                } catch (ClosedSelectorException | AsynchronousCloseException ignore) {
                } catch (IOException e) {
                    throw new ApplicationException(e);
                }
            }
        }, "Server").start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (selector.isOpen()) {
                        // TODO: establish outgoing connections
                        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            if (key.attachment() instanceof ConnectionInfo) {
                                SocketChannel channel = (SocketChannel) key.channel();
                                ConnectionInfo connection = (ConnectionInfo) key.attachment();

                                if (key.isWritable()) {
                                    if (connection.getOutBuffer().hasRemaining()) {
                                        channel.write(connection.getOutBuffer());
                                    }
                                    while (!connection.getOutBuffer().hasRemaining() && !connection.getSendingQueue().isEmpty()) {
                                        MessagePayload payload = connection.getSendingQueue().poll();
                                        if (payload instanceof GetData) {
                                            requestedObjects.addAll(((GetData) payload).getInventory());
                                        }
                                        new NetworkMessage(payload).write(connection.getOutBuffer());
                                    }
                                }
                                if (key.isReadable()) {
                                    channel.read(connection.getInBuffer());
                                    connection.updateReader();
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
        }, "Connections").start();
    }

    @Override
    public void stop() {
        try {
            serverChannel.close();
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
