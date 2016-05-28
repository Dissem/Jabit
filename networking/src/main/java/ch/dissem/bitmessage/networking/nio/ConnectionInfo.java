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

import ch.dissem.bitmessage.entity.MessagePayload;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Created by chrig on 27.05.2016.
 */
public class ConnectionInfo {
    private State state;
    private final Queue<MessagePayload> sendingQueue = new ConcurrentLinkedDeque<>();
    private ByteBuffer in = ByteBuffer.allocate(10);
    private ByteBuffer out = ByteBuffer.allocate(10);

    public State getState() {
        return state;
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

    public enum State {CONNECTING, ACTIVE, DISCONNECTED}
}
