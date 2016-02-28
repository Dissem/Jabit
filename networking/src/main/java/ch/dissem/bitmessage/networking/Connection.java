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

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;
import ch.dissem.bitmessage.exception.NodeException;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

import static ch.dissem.bitmessage.networking.Connection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.Connection.Mode.SYNC;
import static ch.dissem.bitmessage.networking.Connection.State.*;
import static ch.dissem.bitmessage.utils.Singleton.security;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;

/**
 * A connection to a specific node
 */
class Connection {
    public static final int READ_TIMEOUT = 2000;
    private static final Logger LOG = LoggerFactory.getLogger(Connection.class);
    private static final int CONNECT_TIMEOUT = 5000;

    private final long startTime;
    private final ConcurrentMap<InventoryVector, Long> ivCache;
    private final InternalContext ctx;
    private final Mode mode;
    private final Socket socket;
    private final MessageListener listener;
    private final NetworkAddress host;
    private final NetworkAddress node;
    private final Queue<MessagePayload> sendingQueue = new ConcurrentLinkedDeque<>();
    private final Set<InventoryVector> commonRequestedObjects;
    private final Set<InventoryVector> requestedObjects;
    private final long syncTimeout;
    private final ReaderRunnable reader = new ReaderRunnable();
    private final WriterRunnable writer = new WriterRunnable();
    private final DefaultNetworkHandler networkHandler;

    private volatile State state;
    private InputStream in;
    private OutputStream out;
    private int version;
    private long[] streams;
    private int readTimeoutCounter;
    private boolean socketInitialized;
    private long lastObjectTime;

    public Connection(InternalContext context, Mode mode, Socket socket, MessageListener listener,
                      Set<InventoryVector> requestedObjectsMap) throws IOException {
        this(context, mode, listener, socket, requestedObjectsMap,
                Collections.newSetFromMap(new ConcurrentHashMap<InventoryVector, Boolean>(10_000)),
                new NetworkAddress.Builder().ip(socket.getInetAddress()).port(socket.getPort()).stream(1).build(),
                0);
    }

    public Connection(InternalContext context, Mode mode, NetworkAddress node, MessageListener listener,
                      Set<InventoryVector> requestedObjectsMap) {
        this(context, mode, listener, new Socket(), requestedObjectsMap,
                Collections.newSetFromMap(new ConcurrentHashMap<InventoryVector, Boolean>(10_000)),
                node, 0);
    }

    private Connection(InternalContext context, Mode mode, MessageListener listener, Socket socket,
                       Set<InventoryVector> commonRequestedObjects, Set<InventoryVector> requestedObjects, NetworkAddress node, long syncTimeout) {
        this.startTime = UnixTime.now();
        this.ctx = context;
        this.mode = mode;
        this.state = CONNECTING;
        this.listener = listener;
        this.socket = socket;
        this.commonRequestedObjects = commonRequestedObjects;
        this.requestedObjects = requestedObjects;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
        this.node = node;
        this.syncTimeout = (syncTimeout > 0 ? UnixTime.now(+syncTimeout) : 0);
        this.ivCache = new ConcurrentHashMap<>();
        this.networkHandler = (DefaultNetworkHandler) ctx.getNetworkHandler();
    }

    public static Connection sync(InternalContext ctx, InetAddress address, int port, MessageListener listener,
                                  long timeoutInSeconds) throws IOException {
        return new Connection(ctx, SYNC, listener, new Socket(address, port),
                new HashSet<InventoryVector>(),
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

    @SuppressWarnings("RedundantIfStatement")
    private boolean syncFinished(NetworkMessage msg) {
        if (mode != SYNC) {
            return false;
        }
        if (Thread.interrupted()) {
            return true;
        }
        if (state != ACTIVE) {
            return false;
        }
        if (syncTimeout < UnixTime.now()) {
            LOG.info("Synchronization timed out");
            return true;
        }
        if (msg == null) {
            if (requestedObjects.isEmpty() && sendingQueue.isEmpty())
                return true;

            readTimeoutCounter++;
            return readTimeoutCounter > 1;
        } else {
            readTimeoutCounter = 0;
            return false;
        }
    }

    private void activateConnection() {
        LOG.info("Successfully established connection with node " + node);
        state = ACTIVE;
        if (mode != SYNC) {
            sendAddresses();
            ctx.getNodeRegistry().offerAddresses(Collections.singletonList(node));
        }
        sendInventory();
        node.setTime(UnixTime.now());
    }

    private void cleanupIvCache() {
        Long fiveMinutesAgo = UnixTime.now(-5 * MINUTE);
        for (Map.Entry<InventoryVector, Long> entry : ivCache.entrySet()) {
            if (entry.getValue() < fiveMinutesAgo) {
                ivCache.remove(entry.getKey());
            }
        }
    }

    private void updateIvCache(InventoryVector... inventory) {
        cleanupIvCache();
        Long now = UnixTime.now();
        for (InventoryVector iv : inventory) {
            ivCache.put(iv, now);
        }
    }

    private void updateIvCache(List<InventoryVector> inventory) {
        cleanupIvCache();
        Long now = UnixTime.now();
        for (InventoryVector iv : inventory) {
            ivCache.put(iv, now);
        }
    }

    private void receiveMessage(MessagePayload messagePayload) {
        switch (messagePayload.getCommand()) {
            case INV:
                receiveMessage((Inv) messagePayload);
                break;
            case GETDATA:
                receiveMessage((GetData) messagePayload);
                break;
            case OBJECT:
                receiveMessage((ObjectMessage) messagePayload);
                break;
            case ADDR:
                receiveMessage((Addr) messagePayload);
                break;
            case CUSTOM:
            case VERACK:
            case VERSION:
            default:
                throw new IllegalStateException("Unexpectedly received '" + messagePayload.getCommand() + "' command");
        }
    }

    private void receiveMessage(Inv inv) {
        int originalSize = inv.getInventory().size();
        updateIvCache(inv.getInventory());
        List<InventoryVector> missing = ctx.getInventory().getMissing(inv.getInventory(), streams);
        missing.removeAll(commonRequestedObjects);
        LOG.debug("Received inventory with " + originalSize + " elements, of which are "
                + missing.size() + " missing.");
        send(new GetData.Builder().inventory(missing).build());
    }

    private void receiveMessage(GetData getData) {
        for (InventoryVector iv : getData.getInventory()) {
            ObjectMessage om = ctx.getInventory().getObject(iv);
            if (om != null) sendingQueue.offer(om);
        }
    }

    private void receiveMessage(ObjectMessage objectMessage) {
        requestedObjects.remove(objectMessage.getInventoryVector());
        if (ctx.getInventory().contains(objectMessage)) {
            LOG.trace("Received object " + objectMessage.getInventoryVector() + " - already in inventory");
            return;
        }
        try {
            listener.receive(objectMessage);
            security().checkProofOfWork(objectMessage, ctx.getNetworkNonceTrialsPerByte(), ctx.getNetworkExtraBytes());
            ctx.getInventory().storeObject(objectMessage);
            // offer object to some random nodes so it gets distributed throughout the network:
            networkHandler.offer(objectMessage.getInventoryVector());
            lastObjectTime = UnixTime.now();
        } catch (InsufficientProofOfWorkException e) {
            LOG.warn(e.getMessage());
            // DebugUtils.saveToFile(objectMessage); // this line must not be committed active
        } catch (IOException e) {
            LOG.error("Stream " + objectMessage.getStream() + ", object type " + objectMessage.getType() + ": " + e.getMessage(), e);
        } finally {
            if (commonRequestedObjects.remove(objectMessage.getInventoryVector())) {
                LOG.debug("Received object that wasn't requested.");
            }
        }
    }

    private void receiveMessage(Addr addr) {
        LOG.debug("Received " + addr.getAddresses().size() + " addresses.");
        ctx.getNodeRegistry().offerAddresses(addr.getAddresses());
    }

    private void sendAddresses() {
        List<NetworkAddress> addresses = ctx.getNodeRegistry().getKnownAddresses(1000, streams);
        sendingQueue.offer(new Addr.Builder().addresses(addresses).build());
    }

    private void sendInventory() {
        List<InventoryVector> inventory = ctx.getInventory().getInventory(streams);
        for (int i = 0; i < inventory.size(); i += 50000) {
            sendingQueue.offer(new Inv.Builder()
                    .inventory(inventory.subList(i, Math.min(inventory.size(), i + 50000)))
                    .build());
        }
    }

    public void disconnect() {
        state = DISCONNECTED;

        // Make sure objects that are still missing are requested from other nodes
        networkHandler.request(requestedObjects);
    }

    void send(MessagePayload payload) {
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

    public void offer(InventoryVector iv) {
        sendingQueue.offer(new Inv.Builder()
                .addInventoryVector(iv)
                .build());
        updateIvCache(iv);
    }

    public boolean knowsOf(InventoryVector iv) {
        return ivCache.containsKey(iv);
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

    public enum Mode {SERVER, CLIENT, SYNC}

    public enum State {CONNECTING, ACTIVE, DISCONNECTED}

    public class ReaderRunnable implements Runnable {
        @Override
        public void run() {
            lastObjectTime = 0;
            try (Socket socket = Connection.this.socket) {
                initSocket(socket);
                if (mode == CLIENT || mode == SYNC) {
                    send(new Version.Builder().defaults().addrFrom(host).addrRecv(node).build());
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
                switch (state) {
                    case ACTIVE:
                        receiveMessage(msg.getPayload());
                        break;

                    default:
                        handleCommand(msg.getPayload());
                        break;
                }
                if (socket.isClosed() || syncFinished(msg) || checkOpenRequests()) disconnect();
            } catch (SocketTimeoutException ignore) {
                if (state == ACTIVE && syncFinished(null)) disconnect();
            }
        }

        private void handleCommand(MessagePayload payload) {
            switch (payload.getCommand()) {
                case VERSION:
                    handleVersion((Version) payload);
                    break;
                case VERACK:
                    switch (mode) {
                        case SERVER:
                            activateConnection();
                            break;
                        case CLIENT:
                        case SYNC:
                        default:
                            // NO OP
                            break;
                    }
                    break;
                case CUSTOM:
                    MessagePayload response = ctx.getCustomCommandHandler().handle((CustomMessage) payload);
                    if (response != null) {
                        send(response);
                    }
                    disconnect();
                    break;
                default:
                    throw new NodeException("Command 'version' or 'verack' expected, but was '"
                            + payload.getCommand() + "'");
            }
        }

        private void handleVersion(Version version) {
            if (version.getNonce() == ctx.getClientNonce()) {
                LOG.info("Tried to connect to self, disconnecting.");
                disconnect();
            } else if (version.getVersion() >= BitmessageContext.CURRENT_VERSION) {
                Connection.this.version = version.getVersion();
                streams = version.getStreams();
                send(new VerAck());
                switch (mode) {
                    case SERVER:
                        send(new Version.Builder().defaults().addrFrom(host).addrRecv(node).build());
                        break;
                    case CLIENT:
                    case SYNC:
                        activateConnection();
                        break;
                    default:
                        // NO OP
                }
            } else {
                LOG.info("Received unsupported version " + version.getVersion() + ", disconnecting.");
                disconnect();
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
