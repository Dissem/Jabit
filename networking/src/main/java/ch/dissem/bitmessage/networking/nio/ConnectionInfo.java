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

package ch.dissem.bitmessage.networking.nio;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.MessagePayload;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.Version;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.factory.V3MessageReader;
import ch.dissem.bitmessage.networking.AbstractConnection;
import ch.dissem.bitmessage.ports.NetworkHandler;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.CLIENT;
import static ch.dissem.bitmessage.networking.AbstractConnection.Mode.SYNC;
import static ch.dissem.bitmessage.ports.NetworkHandler.MAX_MESSAGE_SIZE;

/**
 * Represents the current state of a connection.
 */
public class ConnectionInfo extends AbstractConnection {
    private ByteBuffer in = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
    private ByteBuffer out = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
    private V3MessageReader reader = new V3MessageReader();

    public ConnectionInfo(InternalContext context, Mode mode,
                          NetworkAddress node, NetworkHandler.MessageListener listener,
                          Set<InventoryVector> commonRequestedObjects) {
        super(context, mode, node, listener, commonRequestedObjects, false);
        out.flip();
        if (mode == CLIENT || mode == SYNC) {
            send(new Version.Builder().defaults(peerNonce).addrFrom(host).addrRecv(node).build());
        }
    }

    public State getState() {
        return state;
    }

    public boolean knowsOf(InventoryVector iv) {
        return ivCache.containsKey(iv);
    }

    public Queue<MessagePayload> getSendingQueue() {
        return sendingQueue;
    }

    public ByteBuffer getInBuffer() {
        return in;
    }

    public ByteBuffer getOutBuffer() {
        return out;
    }

    public void updateReader() {
        reader.update(in);
        if (!reader.getMessages().isEmpty()) {
            Iterator<NetworkMessage> iterator = reader.getMessages().iterator();
            while (iterator.hasNext()) {
                NetworkMessage msg = iterator.next();
                handleMessage(msg.getPayload());
                iterator.remove();
            }
        }
    }

    @Override
    protected void send(MessagePayload payload) {
        sendingQueue.add(payload);
    }
}
