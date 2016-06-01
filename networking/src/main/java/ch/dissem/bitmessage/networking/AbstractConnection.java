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

package ch.dissem.bitmessage.networking;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;
import ch.dissem.bitmessage.exception.NodeException;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static ch.dissem.bitmessage.InternalContext.NETWORK_EXTRA_BYTES;
import static ch.dissem.bitmessage.InternalContext.NETWORK_NONCE_TRIALS_PER_BYTE;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SYNC;
import static ch.dissem.bitmessage.networking.AbstractConnection.State.*;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;

/**
 * Contains everything used by both the old streams-oriented NetworkHandler and the new NioNetworkHandler,
 * respectively their connection objects.
 */
public abstract class AbstractConnection {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConnection.class);
    protected final InternalContext ctx;
    protected final Mode mode;
    protected final NetworkAddress host;
    protected final NetworkAddress node;
    protected final NetworkHandler.MessageListener listener;
    protected final Map<InventoryVector, Long> ivCache;
    protected final Deque<MessagePayload> sendingQueue;
    protected final Set<InventoryVector> commonRequestedObjects;
    protected final Set<InventoryVector> requestedObjects;

    protected volatile State state;
    protected long lastObjectTime;

    protected long peerNonce;
    protected int version;
    protected long[] streams;

    public AbstractConnection(InternalContext context, Mode mode,
                              NetworkAddress node,
                              NetworkHandler.MessageListener listener,
                              Set<InventoryVector> commonRequestedObjects,
                              boolean threadsafe) {
        this.ctx = context;
        this.mode = mode;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
        this.node = node;
        this.listener = listener;
        if (threadsafe) {
            this.ivCache = new ConcurrentHashMap<>();
            this.sendingQueue = new ConcurrentLinkedDeque<>();
            this.requestedObjects = Collections.newSetFromMap(new ConcurrentHashMap<InventoryVector, Boolean>(10_000));
        } else {
            this.ivCache = new HashMap<>();
            this.sendingQueue = new LinkedList<>();
            this.requestedObjects = new HashSet<>();
        }
        this.state = CONNECTING;
        this.commonRequestedObjects = commonRequestedObjects;
    }

    public Mode getMode() {
        return mode;
    }

    public NetworkAddress getNode() {
        return node;
    }

    public State getState() {
        return state;
    }

    protected void handleMessage(MessagePayload payload) {
        switch (state) {
            case ACTIVE:
                receiveMessage(payload);
                break;

            default:
                handleCommand(payload);
                break;
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
            cryptography().checkProofOfWork(objectMessage, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES);
            ctx.getInventory().storeObject(objectMessage);
            // offer object to some random nodes so it gets distributed throughout the network:
            ctx.getNetworkHandler().offer(objectMessage.getInventoryVector());
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

    private void updateIvCache(List<InventoryVector> inventory) {
        cleanupIvCache();
        Long now = UnixTime.now();
        for (InventoryVector iv : inventory) {
            ivCache.put(iv, now);
        }
    }

    public void offer(InventoryVector iv) {
        sendingQueue.offer(new Inv.Builder()
                .addInventoryVector(iv)
                .build());
        updateIvCache(Collections.singletonList(iv));
    }

    public boolean knowsOf(InventoryVector iv) {
        return ivCache.containsKey(iv);
    }

    protected void cleanupIvCache() {
        Long fiveMinutesAgo = UnixTime.now(-5 * MINUTE);
        for (Map.Entry<InventoryVector, Long> entry : ivCache.entrySet()) {
            if (entry.getValue() < fiveMinutesAgo) {
                ivCache.remove(entry.getKey());
            }
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

    protected void activateConnection() {
        LOG.info("Successfully established connection with node " + node);
        state = ACTIVE;
        node.setTime(UnixTime.now());
        if (mode != SYNC) {
            sendAddresses();
            ctx.getNodeRegistry().offerAddresses(Collections.singletonList(node));
        }
        sendInventory();
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

    private void handleVersion(Version version) {
        if (version.getNonce() == ctx.getClientNonce()) {
            LOG.info("Tried to connect to self, disconnecting.");
            disconnect();
        } else if (version.getVersion() >= BitmessageContext.CURRENT_VERSION) {
            this.peerNonce = version.getNonce();
            if (peerNonce == ctx.getClientNonce()) disconnect();

            this.version = version.getVersion();
            this.streams = version.getStreams();
            send(new VerAck());
            switch (mode) {
                case SERVER:
                    send(new Version.Builder().defaults(ctx.getClientNonce()).addrFrom(host).addrRecv(node).build());
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

    public void disconnect() {
        state = DISCONNECTED;

        // Make sure objects that are still missing are requested from other nodes
        ctx.getNetworkHandler().request(requestedObjects);
    }

    protected abstract void send(MessagePayload payload);

    public enum Mode {SERVER, CLIENT, SYNC}

    public enum State {CONNECTING, ACTIVE, DISCONNECTED}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractConnection that = (AbstractConnection) o;
        return Objects.equals(node, that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }
}
