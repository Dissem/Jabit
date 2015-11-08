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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.utils.Property;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Future;

/**
 * Handles incoming messages
 */
public interface NetworkHandler {
    /**
     * Connects to the trusted host, fetches and offers new messages and disconnects afterwards.
     * <p>
     * An implementation should disconnect if either the timeout is reached or the returned thread is interrupted.
     * </p>
     */
    Future<?> synchronize(InetAddress trustedHost, int port, MessageListener listener, long timeoutInSeconds);

    /**
     * Start a full network node, accepting incoming connections and relaying objects.
     */
    void start(MessageListener listener);

    /**
     * Stop the full network node.
     */
    void stop();

    /**
     * Offer new objects to up to 8 random nodes.
     */
    void offer(InventoryVector iv);

    Property getNetworkStatus();

    boolean isRunning();

    interface MessageListener {
        void receive(ObjectMessage object) throws IOException;
    }
}
