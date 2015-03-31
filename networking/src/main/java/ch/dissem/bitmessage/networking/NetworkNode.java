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

import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.Version;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.NetworkMessageReceiver;
import ch.dissem.bitmessage.ports.NetworkMessageSender;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handles all the networky stuff.
 */
public class NetworkNode implements NetworkMessageSender, NetworkMessageReceiver {
    private final BlockingQueue<NetworkMessage> sendingQueue = new LinkedBlockingQueue<>();
    private final ExecutorService pool;

    private final Map<NetworkAddress, Socket> sockets = new HashMap<>();
    private final Map<NetworkAddress, Integer> versions = new HashMap<>();

    /**
     * This is only to be used where it's ignored
     */
    private final static NetworkAddress LOCALHOST = new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build();

    public NetworkNode() {
        pool = Executors.newCachedThreadPool();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        NetworkMessage message = sendingQueue.take();
                        Socket socket = getSocket(message.getTargetNode());
                        message.write(socket.getOutputStream());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "Sender");
    }

    @Override
    public void registerListener(final int port) throws IOException {
        final ServerSocket serverSocket = new ServerSocket(port);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(20000);
                    // FIXME: addd to sockets
                    registerListener(getVersion(null), socket, new MessageListener() {
                        @Override
                        public void receive(NetworkMessage message) {
                            // TODO
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void registerListener(final NetworkAddress node, final MessageListener listener) throws IOException {
        final Socket socket = getSocket(node);
        final int version = getVersion(node);
        sendVersion(node);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    registerListener(version, socket, listener);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendVersion(NetworkAddress node) {
        send(node, new NetworkMessage(node, new Version.Builder().defaults().addrFrom(LOCALHOST).addrRecv(node).build()));
    }

    private void registerListener(int version, Socket socket, MessageListener listener) throws IOException {
        NetworkMessage message = Factory.getNetworkMessage(version, socket.getInputStream());
        if (message.getPayload() instanceof Version) {
            version = ((Version) message.getPayload()).getVersion();
            synchronized (versions) {

                versions.put(new NetworkAddress.Builder()
                        .ip(socket.getInetAddress())
                        .port(socket.getPort())
                        .build(), version);
            }
        }
        listener.receive(message);
    }

    @Override
    public void send(final NetworkAddress node, final NetworkMessage message) {
        sendingQueue.add(message);
    }

    private Socket getSocket(NetworkAddress node) throws IOException {
        synchronized (sockets) {
            Socket socket = sockets.get(node);
            if (socket == null) {
                socket = new Socket(node.toInetAddress(), node.getPort());
                sockets.put(node, socket);
            }
            return socket;
        }
    }

    private synchronized int getVersion(NetworkAddress node) {
        synchronized (versions) {
            Integer version = versions.get(node);
            return version == null ? 3 : version;
        }
    }
}
