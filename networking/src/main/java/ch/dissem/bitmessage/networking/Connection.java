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
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.NetworkHandler.MessageListener;
import ch.dissem.bitmessage.utils.DebugUtils;
import ch.dissem.bitmessage.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static ch.dissem.bitmessage.networking.Connection.State.*;

/**
 * A connection to a specific node
 */
public class Connection implements Runnable {
    private final static Logger LOG = LoggerFactory.getLogger(Connection.class);

    private InternalContext ctx;

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

    public Connection(InternalContext context, State state, Socket socket, MessageListener listener) throws IOException {
        this.ctx = context;
        this.state = state;
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.listener = listener;
        this.host = new NetworkAddress.Builder().ipv6(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).port(0).build();
        this.node = new NetworkAddress.Builder().ip(socket.getInetAddress()).port(socket.getPort()).stream(1).build();
    }

    public State getState() {
        return state;
    }

    public NetworkAddress getNode() {
        return node;
    }

    @Override
    public void run() {
        if (state == CLIENT) {
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
                                if (payload.getVersion() >= BitmessageContext.CURRENT_VERSION) {
                                    this.version = payload.getVersion();
                                    this.streams = payload.getStreams();
                                    send(new VerAck());
                                    state = ACTIVE;
                                    sendAddresses();
                                    sendInventory();
                                } else {
                                    LOG.info("Received unsupported version " + payload.getVersion() + ", disconnecting.");
                                    disconnect();
                                }
                                break;
                            case VERACK:
                                if (state == SERVER) {
                                    send(new Version.Builder().defaults().addrFrom(host).addrRecv(node).build());
                                }
                                break;
                            default:
                                throw new RuntimeException("Command 'version' or 'verack' expected, but was "
                                        + msg.getPayload().getCommand());
                        }
                }
                if (socket.isClosed()) state = DISCONNECTED;
            } catch (SocketTimeoutException ignore) {
                if (state == ACTIVE) {
                    sendQueue();
                }
            }
        }
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
                } catch (InsufficientProofOfWorkException e) {
//                    DebugUtils.saveToFile(objectMessage);
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
        try {
            state = DISCONNECTED;
            socket.close();
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
        }
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

    public enum State {SERVER, CLIENT, ACTIVE, DISCONNECTED}
}
