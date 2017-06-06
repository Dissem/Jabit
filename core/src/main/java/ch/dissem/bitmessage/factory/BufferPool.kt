/*
 * Copyright 2017 Christian Basler
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

package ch.dissem.bitmessage.factory

import ch.dissem.bitmessage.constants.Network.HEADER_SIZE
import ch.dissem.bitmessage.constants.Network.MAX_PAYLOAD_SIZE
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*

/**
 * A pool for [ByteBuffer]s. As they may use up a lot of memory,
 * they should be reused as efficiently as possible.
 */
object BufferPool {
    private val LOG = LoggerFactory.getLogger(BufferPool::class.java)

    private val pools = mapOf(
        HEADER_SIZE to Stack<ByteBuffer>(),
        54 to Stack<ByteBuffer>(),
        1000 to Stack<ByteBuffer>(),
        60000 to Stack<ByteBuffer>(),
        MAX_PAYLOAD_SIZE to Stack<ByteBuffer>()
    )

    @Synchronized fun allocate(capacity: Int): ByteBuffer {
        val targetSize = getTargetSize(capacity)
        val pool = pools[targetSize]
        if (pool == null || pool.isEmpty()) {
            LOG.trace("Creating new buffer of size " + targetSize!!)
            return ByteBuffer.allocate(targetSize)
        } else {
            return pool.pop()
        }
    }

    /**
     * Returns a buffer that has the size of the Bitmessage network message header, 24 bytes.

     * @return a buffer of size 24
     */
    @Synchronized fun allocateHeaderBuffer(): ByteBuffer {
        val pool = pools[HEADER_SIZE]
        if (pool == null || pool.isEmpty()) {
            return ByteBuffer.allocate(HEADER_SIZE)
        } else {
            return pool.pop()
        }
    }

    @Synchronized fun deallocate(buffer: ByteBuffer) {
        buffer.clear()
        val pool = pools[buffer.capacity()]
        pool?.push(buffer) ?: throw IllegalArgumentException("Illegal buffer capacity " + buffer.capacity() +
            " one of " + pools.keys + " expected.")
    }

    private fun getTargetSize(capacity: Int): Int? {
        for (size in pools.keys) {
            if (size >= capacity) return size
        }
        throw IllegalArgumentException("Requested capacity too large: " +
            "requested=" + capacity + "; max=" + MAX_PAYLOAD_SIZE)
    }
}
