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
import ch.dissem.bitmessage.entity.GetData;
import ch.dissem.bitmessage.entity.MessagePayload;
import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.Version;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.NodeException;
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
    private ByteBuffer out = ByteBuffer.allocate(MAX_MESSAGE_SIZE);
    private V3MessageReader reader = new V3MessageReader();
    private boolean syncFinished;
    private long lastUpdate = Long.MAX_VALUE;

    public ConnectionInfo(InternalContext context, Mode mode,
                          NetworkAddress node, NetworkHandler.MessageListener listener,
                          Set<InventoryVector> commonRequestedObjects, long syncTimeout) {
        super(context, mode, node, listener, commonRequestedObjects, syncTimeout);
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
        if (reader == null) {
            throw new NodeException("Node is disconnected");
        }
        return reader.getActiveBuffer();
    }

    public void updateWriter() {
        if ((out == null || !out.hasRemaining()) && !sendingQueue.isEmpty()) {
            out.clear();
            MessagePayload payload = sendingQueue.poll();
            new NetworkMessage(payload).write(out);
            out.flip();
            lastUpdate = System.currentTimeMillis();
        }
    }

    public ByteBuffer getOutBuffer() {
        return out;
    }

    public void updateReader() {
        reader.update();
        if (!reader.getMessages().isEmpty()) {
            Iterator<NetworkMessage> iterator = reader.getMessages().iterator();
            NetworkMessage msg = null;
            while (iterator.hasNext()) {
                msg = iterator.next();
                handleMessage(msg.getPayload());
                iterator.remove();
            }
            syncFinished = syncFinished(msg);
        }
        lastUpdate = System.currentTimeMillis();
    }

    public void updateSyncStatus() {
        if (!syncFinished) {
            syncFinished = reader.getMessages().isEmpty() && syncFinished(null);
        }
    }

    public boolean isExpired() {
        switch (state) {
            case CONNECTING:
                return lastUpdate < System.currentTimeMillis() - 30000;
            case ACTIVE:
                return lastUpdate < System.currentTimeMillis() - 30000;
            case DISCONNECTED:
                return true;
            default:
                throw new IllegalStateException("Unknown state: " + state);
        }
    }

    @Override
    public synchronized void disconnect() {
        super.disconnect();
        if (reader != null) {
            reader.cleanup();
            reader = null;
        }
    }

    public boolean isSyncFinished() {
        return syncFinished;
    }

    @Override
    protected void send(MessagePayload payload) {
        sendingQueue.add(payload);
        if (payload instanceof GetData) {
            requestedObjects.addAll(((GetData) payload).getInventory());
            commonRequestedObjects.addAll(((GetData) payload).getInventory());
        }
    }
}
