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

package ch.dissem.bitmessage.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import static ch.dissem.bitmessage.ports.NetworkHandler.HEADER_SIZE;
import static ch.dissem.bitmessage.ports.NetworkHandler.MAX_PAYLOAD_SIZE;

/**
 * A pool for {@link ByteBuffer}s. As they may use up a lot of memory,
 * they should be reused as efficiently as possible.
 */
class BufferPool {
    private static final Logger LOG = LoggerFactory.getLogger(BufferPool.class);

    public static final BufferPool bufferPool = new BufferPool();

    private final Map<Integer, Stack<ByteBuffer>> pools = new TreeMap<>();

    private BufferPool() {
        pools.put(HEADER_SIZE, new Stack<ByteBuffer>());
        pools.put(54, new Stack<ByteBuffer>());
        pools.put(1000, new Stack<ByteBuffer>());
        pools.put(60000, new Stack<ByteBuffer>());
        pools.put(MAX_PAYLOAD_SIZE, new Stack<ByteBuffer>());
    }

    public synchronized ByteBuffer allocate(int capacity) {
        for (Map.Entry<Integer, Stack<ByteBuffer>> e : pools.entrySet()) {
            if (e.getKey() >= capacity && !e.getValue().isEmpty()) {
                return e.getValue().pop();
            }
        }
        Integer targetSize = getTargetSize(capacity);
        LOG.debug("Creating new buffer of size " + targetSize);
        return ByteBuffer.allocate(targetSize);
    }

    /**
     * Returns a buffer that has the size of the Bitmessage network message header, 24 bytes.
     *
     * @return a buffer of size 24
     */
    public synchronized ByteBuffer allocateHeaderBuffer() {
        Stack<ByteBuffer> pool = pools.get(HEADER_SIZE);
        if (!pool.isEmpty()) {
            return pool.pop();
        } else {
            return ByteBuffer.allocate(HEADER_SIZE);
        }
    }

    public synchronized void deallocate(ByteBuffer buffer) {
        buffer.clear();

        if (!pools.keySet().contains(buffer.capacity())) {
            throw new IllegalArgumentException("Illegal buffer capacity " + buffer.capacity() +
                " one of " + pools.keySet() + " expected.");
        }
        pools.get(buffer.capacity()).push(buffer);
    }

    private Integer getTargetSize(int capacity) {
        for (Integer size : pools.keySet()) {
            if (size >= capacity) return size;
        }
        throw new IllegalArgumentException("Requested capacity too large: " +
            "requested=" + capacity + "; max=" + MAX_PAYLOAD_SIZE);
    }
}
