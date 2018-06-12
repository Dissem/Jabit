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
import ch.dissem.bitmessage.exception.NodeException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.max

/**
 * A pool for [ByteBuffer]s. As they may use up a lot of memory,
 * they should be reused as efficiently as possible.
 */
object BufferPool {
    private val LOG = LoggerFactory.getLogger(BufferPool::class.java)

    private var limit: Int? = null
    private var strictLimit = false

    /**
     * Sets a limit to how many buffers the pool handles. If strict is set to true, it will not issue any
     * buffers once the limit is reached and will throw a NodeException instead. Otherwise, it will simply
     * ignore returned buffers once the limit is reached (and therefore garbage collected)
     */
    fun setLimit(limit: Int, strict: Boolean = false) {
        this.limit = limit
        this.strictLimit = strict
        pools.values.forEach { it.limit = limit }
        pools[HEADER_SIZE]!!.limit = 2 * limit
        pools[MAX_PAYLOAD_SIZE]!!.limit = max(limit / 2, 1)
    }

    private val pools = mapOf(
        HEADER_SIZE to Pool(),
        54 to Pool(),
        1000 to Pool(),
        60000 to Pool(),
        MAX_PAYLOAD_SIZE to Pool()
    )

    @Synchronized
    fun allocate(capacity: Int): ByteBuffer {
        val targetSize = getTargetSize(capacity)
        val pool = pools[targetSize] ?: throw IllegalStateException("No pool for size $targetSize available")

        return if (pool.isEmpty) {
            if (pool.hasCapacity || !strictLimit) {
                LOG.trace("Creating new buffer of size $targetSize")
                ByteBuffer.allocate(targetSize)
            } else {
                throw NodeException("pool limit for capacity $capacity is reached")
            }
        } else {
            pool.pop()
        }
    }

    /**
     * Returns a buffer that has the size of the Bitmessage network message header, 24 bytes.

     * @return a buffer of size 24
     */
    @Synchronized
    fun allocateHeaderBuffer(): ByteBuffer {
        val pool = pools[HEADER_SIZE] ?: throw IllegalStateException("No pool for header available")
        return if (pool.isEmpty) {
            if (pool.hasCapacity || !strictLimit) {
                LOG.trace("Creating new buffer of header")
                ByteBuffer.allocate(HEADER_SIZE)
            } else {
                throw NodeException("pool limit for header buffer is reached")
            }
        } else {
            pool.pop()
        }
    }

    @Synchronized
    fun deallocate(buffer: ByteBuffer) {
        buffer.clear()
        val pool = pools[buffer.capacity()]
            ?: throw IllegalArgumentException("Illegal buffer capacity ${buffer.capacity()} one of ${pools.keys} expected.")
        pool.push(buffer)
    }

    private fun getTargetSize(capacity: Int): Int {
        for (size in pools.keys) {
            if (size >= capacity) return size
        }
        throw IllegalArgumentException("Requested capacity too large: requested=$capacity; max=$MAX_PAYLOAD_SIZE")
    }

    /**
     * There is a race condition where the limit could be ignored for an allocation, but I think the consequences
     * are benign.
     */
    class Pool {
        private val stack = Stack<ByteBuffer>()
        private var capacity = 0
        internal var limit: Int? = null
            set(value) {
                capacity = value ?: 0
                field = value
            }

        val isEmpty
            get() = stack.isEmpty()

        val hasCapacity
            @Synchronized
            get() = limit == null || capacity > 0

        @Synchronized
        fun pop(): ByteBuffer {
            capacity--
            return stack.pop()
        }

        @Synchronized
        fun push(buffer: ByteBuffer) {
            if (hasCapacity) {
                stack.push(buffer)
            }
            // else, let it be collected by the garbage collector
            capacity++
        }
    }
}
