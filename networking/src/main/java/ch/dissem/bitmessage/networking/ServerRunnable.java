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

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SERVER;

/**
 * @author Christian Basler
 */
@Deprecated
public class ServerRunnable implements Runnable, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ServerRunnable.class);
    private final InternalContext ctx;
    private final ServerSocket serverSocket;
    private final DefaultNetworkHandler networkHandler;

    public ServerRunnable(InternalContext ctx, DefaultNetworkHandler networkHandler) throws IOException {
        this.ctx = ctx;
        this.networkHandler = networkHandler;
        this.serverSocket = new ServerSocket(ctx.getPort());
    }

    @Override
    public void run() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(Connection.READ_TIMEOUT);
                networkHandler.startConnection(new Connection(ctx, SERVER, socket, networkHandler.requestedObjects));
            } catch (IOException e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
        }
    }
}
