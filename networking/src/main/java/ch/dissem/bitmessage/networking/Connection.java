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
import static ch.dissem.bitmessage.networking.Connection.State.*;
import static ch.dissem.bitmessage.utils.Singleton.security;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;

/**
 * A connection to a specific node
 */
public class Connection implements Runnable {
    public static final int READ_TIMEOUT = 2000;
    private final static Logger LOG = LoggerFactory.getLogger(Connection.class);
    private static final int CONNECT_TIMEOUT = 5000;
    private final ConcurrentMap<InventoryVector, Long> ivCache;
    private final InternalContext ctx;
    private final Mode mode;
    private final Socket socket;
    private final MessageListener listener;
    private final NetworkAddress host;
    private final NetworkAddress node;
    private final Queue<MessagePayload> sendingQueue = new ConcurrentLinkedDeque<>();
    private final Map<InventoryVector, Long> requestedObjects;
    private final long syncTimeout;

    private State state;
    private InputStream in;
    private OutputStream out;
    private int version;
    private long[] streams;
    private int readTimeoutCounter;

    public Connection(InternalContext context, Mode mode, Socket socket, MessageListener listener,
                      ConcurrentMap<InventoryVector, Long> requestedObjectsMap) throws IOException {
        this(context, mode, listener, socket, requestedObjectsMap,
                new NetworkAddress.Builder().ip(socket.getInetAddress()).port(socket.getPort()).stream(1).build(),
                0);
    }

    public Connection(InternalContext context, Mode mode, NetworkAddress node, MessageListener listener,
                      ConcurrentMap<InventoryVector, Long> requestedObjectsMap) {
        this(context, mode, listener, new Socket(), requestedObjectsMap,
                node, 0);
    }

    private Connection(InternalContext context, Mode mode, MessageListener listener, Socket socket,
                       Map<InventoryVector, Long> requestedObjectsMap, NetworkAddress node, long syncTimeout) {
        this.ctx = context;
        this.mode = mode;
        this.state = CONNECTING;
        this.listener = listener;
        this.socket = socket;
        this.requestedObjects = requestedObjectsMap;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
        this.node = node;
        this.syncTimeout = (syncTimeout > 0 ? UnixTime.now(+syncTimeout) : 0);
        this.ivCache = new ConcurrentHashMap<>();
    }

    public static Connection sync(InternalContext ctx, InetAddress address, int port, MessageListener listener,
                                  long timeoutInSeconds) throws IOException {
        return new Connection(ctx, Mode.CLIENT, listener, new Socket(address, port),
                new HashMap<InventoryVector, Long>(),
                new NetworkAddress.Builder().ip(address).port(port).stream(1).build(),
                timeoutInSeconds);
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
    public void run() {
        try (Socket socket = this.socket) {
            if (!socket.isConnected()) {
                LOG.debug("Trying to connect to node " + node);
                socket.connect(new InetSocketAddress(node.toInetAddress(), node.getPort()), CONNECT_TIMEOUT);
            }
            socket.setSoTimeout(READ_TIMEOUT);
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            if (mode == CLIENT) {
                send(new Version.Builder().defaults().addrFrom(host).addrRecv(node).build());
            }
            while (state != DISCONNECTED) {
                try {
                    NetworkMessage msg = Factory.getNetworkMessage(version, in);
                    if (msg == null)
                        continue;
                    switch (state) {
                        case ACTIVE:
                            receiveMessage(msg.getPayload());
                            sendQueue();
                            break;

                        default:
                            switch (msg.getPayload().getCommand()) {
                                case VERSION:
                                    Version payload = (Version) msg.getPayload();
                                    if (payload.getNonce() == ctx.getClientNonce()) {
                                        LOG.info("Tried to connect to self, disconnecting.");
                                        disconnect();
                                    } else if (payload.getVersion() >= BitmessageContext.CURRENT_VERSION) {
                                        this.version = payload.getVersion();
                                        this.streams = payload.getStreams();
                                        send(new VerAck());
                                        switch (mode) {
                                            case SERVER:
                                                send(new Version.Builder().defaults().addrFrom(host).addrRecv(node).build());
                                                break;
                                            case CLIENT:
                                                activateConnection();
                                                break;
                                        }
                                    } else {
                                        LOG.info("Received unsupported version " + payload.getVersion() + ", disconnecting.");
                                        disconnect();
                                    }
                                    break;
                                case VERACK:
                                    switch (mode) {
                                        case SERVER:
                                            activateConnection();
                                            break;
                                        case CLIENT:
                                            // NO OP
                                            break;
                                    }
                                    break;
                                default:
                                    throw new NodeException("Command 'version' or 'verack' expected, but was '"
                                            + msg.getPayload().getCommand() + "'");
                            }
                    }
                    if (socket.isClosed() || syncFinished(msg)) disconnect();
                } catch (SocketTimeoutException ignore) {
                    if (state == ACTIVE) {
                        sendQueue();
                        if (syncFinished(null)) disconnect();
                    }
                }
            }
        } catch (IOException | NodeException e) {
            disconnect();
            LOG.debug("Disconnected from node " + node + ": " + e.getMessage());
        } catch (RuntimeException e) {
            disconnect();
            throw e;
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean syncFinished(NetworkMessage msg) {
        if (syncTimeout == 0 || state != ACTIVE) {
            return false;
        }
        if (syncTimeout < UnixTime.now()) {
            return true;
        }
        if (msg == null) {
            readTimeoutCounter++;
            return readTimeoutCounter > 1;
        }
        readTimeoutCounter = 0;
        if (!(msg.getPayload() instanceof Addr) && !(msg.getPayload() instanceof GetData)
                && requestedObjects.isEmpty() && sendingQueue.isEmpty()) {
            return true;
        }
        return false;
    }

    private void activateConnection() {
        LOG.info("Successfully established connection with node " + node);
        state = ACTIVE;
        sendAddresses();
        sendInventory();
        node.setTime(UnixTime.now());
        ctx.getNodeRegistry().offerAddresses(Collections.singletonList(node));
    }

    private void sendQueue() {
        if (sendingQueue.size() > 0) {
            LOG.debug("Sending " + sendingQueue.size() + " messages to node " + node);
        }
        for (MessagePayload msg = sendingQueue.poll(); msg != null; msg = sendingQueue.poll()) {
            send(msg);
        }
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

    private void updateRequestedObjects(List<InventoryVector> missing) {
        Long now = UnixTime.now();
        Long fiveMinutesAgo = now - 5 * MINUTE;
        Long tenMinutesAgo = now - 10 * MINUTE;
        List<InventoryVector> stillMissing = new LinkedList<>();
        for (Map.Entry<InventoryVector, Long> entry : requestedObjects.entrySet()) {
            if (entry.getValue() < fiveMinutesAgo) {
                stillMissing.add(entry.getKey());
                // If it's still not available after 10 minutes, we won't look for it
                // any longer (except it's announced again)
                if (entry.getValue() < tenMinutesAgo) {
                    requestedObjects.remove(entry.getKey());
                }
            }
        }

        for (InventoryVector iv : missing) {
            requestedObjects.put(iv, now);
        }
        if (!stillMissing.isEmpty()) {
            LOG.debug(stillMissing.size() + " items are still missing.");
            missing.addAll(stillMissing);
        }
    }

    private void receiveMessage(MessagePayload messagePayload) {
        switch (messagePayload.getCommand()) {
            case INV:
                Inv inv = (Inv) messagePayload;
                updateIvCache(inv.getInventory());
                List<InventoryVector> missing = ctx.getInventory().getMissing(inv.getInventory(), streams);
                missing.removeAll(requestedObjects.keySet());
                LOG.debug("Received inventory with " + inv.getInventory().size() + " elements, of which are "
                        + missing.size() + " missing.");
                updateRequestedObjects(missing);
                send(new GetData.Builder().inventory(missing).build());
                break;
            case GETDATA:
                GetData getData = (GetData) messagePayload;
                for (InventoryVector iv : getData.getInventory()) {
                    ObjectMessage om = ctx.getInventory().getObject(iv);
                    if (om != null) sendingQueue.offer(om);
                }
                break;
            case OBJECT:
                ObjectMessage objectMessage = (ObjectMessage) messagePayload;
                try {
                    LOG.debug("Received object " + objectMessage.getInventoryVector());
                    security().checkProofOfWork(objectMessage, ctx.getNetworkNonceTrialsPerByte(), ctx.getNetworkExtraBytes());
                    if (ctx.getInventory().contains(objectMessage))
                        break;
                    listener.receive(objectMessage);
                    ctx.getInventory().storeObject(objectMessage);
                    // offer object to some random nodes so it gets distributed throughout the network:
                    // FIXME: don't do this while we catch up after initialising our first connection
                    // (that might be a bit tricky to do)
                    ctx.getNetworkHandler().offer(objectMessage.getInventoryVector());
                } catch (InsufficientProofOfWorkException e) {
                    LOG.warn(e.getMessage());
                    // DebugUtils.saveToFile(objectMessage); // this line must not be committed active
                } catch (IOException e) {
                    LOG.error("Stream " + objectMessage.getStream() + ", object type " + objectMessage.getType() + ": " + e.getMessage(), e);
                } finally {
                    requestedObjects.remove(objectMessage.getInventoryVector());
                }
                break;
            case ADDR:
                Addr addr = (Addr) messagePayload;
                LOG.debug("Received " + addr.getAddresses().size() + " addresses.");
                ctx.getNodeRegistry().offerAddresses(addr.getAddresses());
                break;
            case VERACK:
            case VERSION:
                throw new RuntimeException("Unexpectedly received '" + messagePayload.getCommand() + "' command");
        }
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
    }

    private void send(MessagePayload payload) {
        try {
            new NetworkMessage(payload).write(out);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            disconnect();
        }
    }

    public void offer(InventoryVector iv) {
        LOG.debug("Offering " + iv + " to node " + node.toString());
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

    public void request(InventoryVector key) {
        sendingQueue.offer(new GetData.Builder()
                        .addInventoryVector(key)
                        .build()
        );
    }

    public enum Mode {SERVER, CLIENT}

    public enum State {CONNECTING, ACTIVE, DISCONNECTED}
}
