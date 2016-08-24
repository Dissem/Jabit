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
import ch.dissem.bitmessage.entity.*;
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

/**
 * Represents the current state of a connection.
 */
public class ConnectionInfo extends AbstractConnection {
    private final ByteBuffer headerOut = ByteBuffer.allocate(24);
    private ByteBuffer payloadOut;
    private V3MessageReader reader = new V3MessageReader();
    private boolean syncFinished;
    private long lastUpdate = System.currentTimeMillis();

    public ConnectionInfo(InternalContext context, Mode mode,
                          NetworkAddress node, NetworkHandler.MessageListener listener,
                          Set<InventoryVector> commonRequestedObjects, long syncTimeout) {
        super(context, mode, node, listener, commonRequestedObjects, syncTimeout);
        headerOut.flip();
        if (mode == CLIENT || mode == SYNC) {
            send(new Version.Builder().defaults(ctx.getClientNonce()).addrFrom(host).addrRecv(node).build());
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
        if (!headerOut.hasRemaining() && !sendingQueue.isEmpty()) {
            headerOut.clear();
            MessagePayload payload = sendingQueue.poll();
            payloadOut = new NetworkMessage(payload).writeHeaderAndGetPayloadBuffer(headerOut);
            headerOut.flip();
            lastUpdate = System.currentTimeMillis();
        }
    }

    public ByteBuffer[] getOutBuffers() {
        return new ByteBuffer[]{headerOut, payloadOut};
    }

    public void cleanupBuffers() {
        if (payloadOut != null && !payloadOut.hasRemaining()) {
            payloadOut = null;
        }
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
            syncFinished = (reader == null || reader.getMessages().isEmpty()) && syncFinished(null);
        }
    }

    public boolean isExpired() {
        switch (state) {
            case CONNECTING:
                // the TCP timeout starts out at 20 seconds
                return lastUpdate < System.currentTimeMillis() - 20_000;
            case ACTIVE:
                // after verack messages are exchanged, the timeout is raised to 10 minutes
                return lastUpdate < System.currentTimeMillis() - 600_000;
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

    public boolean isWritePending() {
        return !sendingQueue.isEmpty()
            || headerOut != null && headerOut.hasRemaining()
            || payloadOut != null && payloadOut.hasRemaining();
    }
}
