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
import ch.dissem.bitmessage.entity.GetData;
import ch.dissem.bitmessage.entity.MessagePayload;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.Version;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.NetworkHandler.MessageListener;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SYNC;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.ACTIVE;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.DISCONNECTED;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;

/**
 * A connection to a specific node
 */
class Connection extends AbstractConnection {
    public static final int READ_TIMEOUT = 2000;
    private static final Logger LOG = LoggerFactory.getLogger(Connection.class);
    private static final int CONNECT_TIMEOUT = 5000;

    private final long startTime;
    private final Socket socket;
    private final ReaderRunnable reader = new ReaderRunnable();
    private final WriterRunnable writer = new WriterRunnable();

    private InputStream in;
    private OutputStream out;
    private boolean socketInitialized;

    public Connection(InternalContext context, Mode mode, Socket socket, MessageListener listener,
                      Set<InventoryVector> requestedObjectsMap) throws IOException {
        this(context, mode, listener, socket, requestedObjectsMap,
                new NetworkAddress.Builder().ip(socket.getInetAddress()).port(socket.getPort()).stream(1).build(),
                0);
    }

    public Connection(InternalContext context, Mode mode, NetworkAddress node, MessageListener listener,
                      Set<InventoryVector> requestedObjectsMap) {
        this(context, mode, listener, new Socket(), requestedObjectsMap,
                node, 0);
    }

    private Connection(InternalContext context, Mode mode, MessageListener listener, Socket socket,
                       Set<InventoryVector> commonRequestedObjects, NetworkAddress node, long syncTimeout) {
        super(context, mode, node, listener, commonRequestedObjects, syncTimeout);
        this.startTime = UnixTime.now();
        this.socket = socket;
    }

    public static Connection sync(InternalContext ctx, InetAddress address, int port, MessageListener listener,
                                  long timeoutInSeconds) throws IOException {
        return new Connection(ctx, SYNC, listener, new Socket(address, port),
                new HashSet<InventoryVector>(),
                new NetworkAddress.Builder().ip(address).port(port).stream(1).build(),
                timeoutInSeconds);
    }

    public long getStartTime() {
        return startTime;
    }

    public Mode getMode() {
        return mode;
    }

    public State getState() {
        return state;
    }

    public NetworkAddress getNode() {
        return node;
    }

    @Override
    protected void send(MessagePayload payload) {
        try {
            if (payload instanceof GetData) {
                requestedObjects.addAll(((GetData) payload).getInventory());
            }
            synchronized (this) {
                new NetworkMessage(payload).write(out);
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            disconnect();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Connection that = (Connection) o;
        return Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }

    private synchronized void initSocket(Socket socket) throws IOException {
        if (!socketInitialized) {
            if (!socket.isConnected()) {
                LOG.trace("Trying to connect to node " + node);
                socket.connect(new InetSocketAddress(node.toInetAddress(), node.getPort()), CONNECT_TIMEOUT);
            }
            socket.setSoTimeout(READ_TIMEOUT);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            socketInitialized = true;
        }
    }

    public ReaderRunnable getReader() {
        return reader;
    }

    public WriterRunnable getWriter() {
        return writer;
    }

    public class ReaderRunnable implements Runnable {
        @Override
        public void run() {
            try (Socket socket = Connection.this.socket) {
                initSocket(socket);
                if (mode == CLIENT || mode == SYNC) {
                    send(new Version.Builder().defaults(peerNonce).addrFrom(host).addrRecv(node).build());
                }
                while (state != DISCONNECTED) {
                    if (mode != SYNC) {
                        if (state == ACTIVE && requestedObjects.isEmpty() && sendingQueue.isEmpty()) {
                            Thread.sleep(1000);
                        } else {
                            Thread.sleep(100);
                        }
                    }
                    receive();
                }
            } catch (Exception e) {
                LOG.trace("Reader disconnected from node " + node + ": " + e.getMessage());
            } finally {
                disconnect();
                try {
                    socket.close();
                } catch (Exception e) {
                    LOG.debug(e.getMessage(), e);
                }
            }
        }

        private void receive() throws InterruptedException {
            try {
                NetworkMessage msg = Factory.getNetworkMessage(version, in);
                if (msg == null)
                    return;
                handleMessage(msg.getPayload());
                if (socket.isClosed() || syncFinished(msg) || checkOpenRequests()) disconnect();
            } catch (SocketTimeoutException ignore) {
                if (state == ACTIVE && syncFinished(null)) disconnect();
            }
        }

    }

    private boolean checkOpenRequests() {
        return !requestedObjects.isEmpty() && lastObjectTime > 0 && (UnixTime.now() - lastObjectTime) > 2 * MINUTE;
    }

    public class WriterRunnable implements Runnable {
        @Override
        public void run() {
            try (Socket socket = Connection.this.socket) {
                initSocket(socket);
                while (state != DISCONNECTED) {
                    if (sendingQueue.isEmpty()) {
                        Thread.sleep(1000);
                    } else {
                        send(sendingQueue.poll());
                    }
                }
            } catch (IOException | InterruptedException e) {
                LOG.trace("Writer disconnected from node " + node + ": " + e.getMessage());
                disconnect();
            }
        }
    }
}
