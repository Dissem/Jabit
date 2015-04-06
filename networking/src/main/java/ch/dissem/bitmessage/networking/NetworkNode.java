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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ch.dissem.bitmessage.networking.Connection.State.CLIENT;
import static ch.dissem.bitmessage.networking.Connection.State.SERVER;

/**
 * Handles all the networky stuff.
 */
public class NetworkNode implements NetworkMessageSender, NetworkMessageReceiver {
    private final static Logger LOG = LoggerFactory.getLogger(NetworkNode.class);
    /**
     * This is only to be used where it's ignored
     */
    private final static NetworkAddress LOCALHOST = new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build();
    private final ExecutorService pool;

    public NetworkNode() {
        pool = Executors.newCachedThreadPool();

        // TODO: sending
//        Thread sender = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        NetworkMessage message = sendingQueue.take();
//
//                        try (Socket socket = getSocket(message.getTargetNode())) {
//                            message.write(socket.getOutputStream());
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    } catch (InterruptedException e) {
//                        // Ignore?
//                    }
//                }
//            }
//        }, "Sender");
//        sender.setDaemon(true);
//        sender.start();
    }

    @Override
    public void registerListener(final int port, final MessageListener listener) throws IOException {
        final ServerSocket serverSocket = new ServerSocket(port);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                NetworkAddress address = null;
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(20000);
                    pool.execute(new Connection(SERVER, socket, listener));
                } catch (IOException e) {
                    LOG.debug(e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void registerListener(final NetworkAddress node, final MessageListener listener) throws IOException {
        pool.execute(new Connection(CLIENT, new Socket(node.toInetAddress(), node.getPort()), listener));
    }

    @Override
    public void send(final NetworkAddress node, final NetworkMessage message) {
        // TODO: sendingQueue.add(message);
    }
}
