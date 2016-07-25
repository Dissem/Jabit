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

import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * A pool for {@link ByteBuffer}s. As they may use up a lot of memory,
 * they should be reused as efficiently as possible.
 */
class BufferPool {
    private static final Logger LOG = LoggerFactory.getLogger(BufferPool.class);

    public static final BufferPool bufferPool = new BufferPool(256, 2048);

    private final Map<Size, Integer> capacities = new EnumMap<>(Size.class);
    private final Map<Size, Stack<ByteBuffer>> pools = new EnumMap<>(Size.class);

    private BufferPool(int small, int medium) {
        capacities.put(Size.HEADER, 24);
        capacities.put(Size.SMALL, small);
        capacities.put(Size.MEDIUM, medium);
        capacities.put(Size.LARGE, NetworkHandler.MAX_PAYLOAD_SIZE);
        pools.put(Size.HEADER, new Stack<ByteBuffer>());
        pools.put(Size.SMALL, new Stack<ByteBuffer>());
        pools.put(Size.MEDIUM, new Stack<ByteBuffer>());
        pools.put(Size.LARGE, new Stack<ByteBuffer>());
    }

    public synchronized ByteBuffer allocate(int capacity) {
        Size targetSize = getTargetSize(capacity);
        Size s = targetSize;
        do {
            Stack<ByteBuffer> pool = pools.get(s);
            if (!pool.isEmpty()) {
                return pool.pop();
            }
            s = s.next();
        } while (s != null);
        LOG.debug("Creating new buffer of size " + targetSize);
        return ByteBuffer.allocate(capacities.get(targetSize));
    }

    public synchronized ByteBuffer allocate() {
        Stack<ByteBuffer> pool = pools.get(Size.HEADER);
        if (!pool.isEmpty()) {
            return pool.pop();
        } else {
            return ByteBuffer.allocate(capacities.get(Size.HEADER));
        }
    }

    public synchronized void deallocate(ByteBuffer buffer) {
        buffer.clear();
        Size size = getTargetSize(buffer.capacity());
        if (buffer.capacity() != capacities.get(size)) {
            throw new IllegalArgumentException("Illegal buffer capacity " + buffer.capacity() +
                " one of " + capacities.values() + " expected.");
        }
        pools.get(size).push(buffer);
    }

    private Size getTargetSize(int capacity) {
        for (Size s : Size.values()) {
            if (capacity <= capacities.get(s)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Requested capacity too large: " +
            "requested=" + capacity + "; max=" + capacities.get(Size.LARGE));
    }


    private enum Size {
        HEADER, SMALL, MEDIUM, LARGE;

        public Size next() {
            switch (this) {
                case SMALL:
                    return MEDIUM;
                case MEDIUM:
                    return LARGE;
                default:
                    return null;
            }
        }
    }
}
