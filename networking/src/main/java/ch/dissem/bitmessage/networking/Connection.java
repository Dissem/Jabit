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
import ch.dissem.bitmessage.utils.DebugUtils;
import ch.dissem.bitmessage.utils.Security;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static ch.dissem.bitmessage.networking.Connection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.Connection.State.*;

/**
 * A connection to a specific node
 */
public class Connection implements Runnable {
    private final static Logger LOG = LoggerFactory.getLogger(Connection.class);
    private static final int CONNECT_TIMEOUT = 10000;

    private InternalContext ctx;

    private Mode mode;
    private State state;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private MessageListener listener;

    private int version;
    private long[] streams;

    private NetworkAddress host;
    private NetworkAddress node;

    private Queue<MessagePayload> sendingQueue = new ConcurrentLinkedDeque<>();

    public Connection(InternalContext context, Mode mode, Socket socket, MessageListener listener) throws IOException {
        this.ctx = context;
        this.mode = mode;
        this.state = CONNECTING;
        this.socket = socket;
        this.listener = listener;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
        this.node = new NetworkAddress.Builder().ip(socket.getInetAddress()).port(socket.getPort()).stream(1).build();
    }

    public Connection(InternalContext context, Mode mode, NetworkAddress node, MessageListener listener) {
        this.ctx = context;
        this.mode = mode;
        this.state = CONNECTING;
        this.socket = new Socket();
        this.node = node;
        this.listener = listener;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
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
                socket.connect(new InetSocketAddress(node.toInetAddress(), node.getPort()), CONNECT_TIMEOUT);
            }
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
                    if (socket.isClosed()) state = DISCONNECTED;
                } catch (SocketTimeoutException ignore) {
                    if (state == ACTIVE) {
                        sendQueue();
                    }
                }
            }
        } catch (IOException | NodeException e) {
            LOG.debug("disconnection from node " + node + ": " + e.getMessage(), e);
            disconnect();
        } catch (RuntimeException e) {
            disconnect();
            throw e;
        }
    }

    private void activateConnection() {
        state = ACTIVE;
        sendAddresses();
        sendInventory();
        node.setTime(UnixTime.now());
        ctx.getNodeRegistry().offerAddresses(Arrays.asList(node));
    }

    private void sendQueue() {
        LOG.debug("Sending " + sendingQueue.size() + " messages to node " + node);
        for (MessagePayload msg = sendingQueue.poll(); msg != null; msg = sendingQueue.poll()) {
            send(msg);
        }
    }

    private void receiveMessage(MessagePayload messagePayload) {
        switch (messagePayload.getCommand()) {
            case INV:
                Inv inv = (Inv) messagePayload;
                List<InventoryVector> missing = ctx.getInventory().getMissing(inv.getInventory(), streams);
                LOG.debug("Received inventory with " + inv.getInventory().size() + " elements, of which are "
                        + missing.size() + " missing.");
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
                    Security.checkProofOfWork(objectMessage, ctx.getNetworkNonceTrialsPerByte(), ctx.getNetworkExtraBytes());
                    listener.receive(objectMessage);
                    ctx.getInventory().storeObject(objectMessage);
                    // offer object to some random nodes so it gets distributed throughout the network:
                    // FIXME: don't do this while we catch up after initialising our first connection
                    // (that might be a bit tricky to do)
                    ctx.getNetworkHandler().offer(objectMessage.getInventoryVector());
                } catch (InsufficientProofOfWorkException e) {
                    LOG.warn(e.getMessage());
                } catch (IOException e) {
                    LOG.error("Stream " + objectMessage.getStream() + ", object type " + objectMessage.getType() + ": " + e.getMessage(), e);
                    DebugUtils.saveToFile(objectMessage);
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

    public enum Mode {SERVER, CLIENT}

    public enum State {CONNECTING, ACTIVE, DISCONNECTED}
}
