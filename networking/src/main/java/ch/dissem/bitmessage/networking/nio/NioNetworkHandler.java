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
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;

import static java.nio.channels.SelectionKey.*;

/**
 * Network handler using java.nio, resulting in less threads.
 */
public class NioNetworkHandler implements NetworkHandler, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(NioNetworkHandler.class);

    private InternalContext ctx;
    private Selector selector;

    @Override
    public Future<?> synchronize(InetAddress server, int port, MessageListener listener, long timeoutInSeconds) {
        return null;
    }

    @Override
    public CustomMessage send(InetAddress server, int port, CustomMessage request) {
        return null;
    }

    @Override
    public void start(MessageListener listener) {
        if (listener == null) {
            throw new IllegalStateException("Listener must be set at start");
        }
        if (selector != null && selector.isOpen()) {
            throw new IllegalStateException("Network already running - you need to stop first.");
        }
        try {
            final Set<InventoryVector> requestedObjects = new HashSet<>();
            selector = Selector.open();
            {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.bind(new InetSocketAddress(ctx.getPort()));
                server.register(selector, OP_ACCEPT);
            }
            while (selector.isOpen()) {
                // TODO: establish outgoing connections
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isAcceptable()) {
                        SocketChannel accepted = ((ServerSocketChannel) key.channel()).accept();
                        accepted.configureBlocking(false);
                        accepted.register(selector, OP_READ | OP_WRITE).attach(new ConnectionInfo());
                    }
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
                            // TODO
                            channel.read(connection.getInBuffer());
                        }
                    }
                    keyIterator.remove();
                }
            }
            selector.close();
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public void offer(InventoryVector iv) {

    }

    @Override
    public Property getNetworkStatus() {
        return null;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }
}
