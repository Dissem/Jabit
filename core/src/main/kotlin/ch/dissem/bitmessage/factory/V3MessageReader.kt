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

import ch.dissem.bitmessage.constants.Network.MAX_PAYLOAD_SIZE
import ch.dissem.bitmessage.entity.NetworkMessage
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.utils.Decode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * Similar to the [V3MessageFactory], but used for NIO buffers which may or may not contain a whole message.
 */
class V3MessageReader {
    private var headerBuffer: ByteBuffer? = null
    private var dataBuffer: ByteBuffer? = null

    private var state: ReaderState? = ReaderState.MAGIC
    private var command: String? = null
    private var length: Int = 0
    private val checksum = ByteArray(4)

    private val messages = LinkedList<NetworkMessage>()

    fun getActiveBuffer(): ByteBuffer {
        if (state != null && state != ReaderState.DATA) {
            if (headerBuffer == null) {
                headerBuffer = BufferPool.allocateHeaderBuffer()
            }
        }
        return if (state == ReaderState.DATA)
            dataBuffer ?: throw IllegalStateException("data buffer is null")
        else
            headerBuffer ?: throw IllegalStateException("header buffer is null")
    }

    fun update() {
        if (state != ReaderState.DATA) {
            getActiveBuffer() // in order to initialize
            headerBuffer?.flip() ?: throw IllegalStateException("header buffer is null")
        }
        when (state) {
            V3MessageReader.ReaderState.MAGIC -> magic(headerBuffer ?: throw IllegalStateException("header buffer is null"))
            V3MessageReader.ReaderState.HEADER -> header(headerBuffer ?: throw IllegalStateException("header buffer is null"))
            V3MessageReader.ReaderState.DATA -> data(dataBuffer ?: throw IllegalStateException("data buffer is null"))
        }
    }

    private fun magic(headerBuffer: ByteBuffer) {
        if (!findMagicBytes(headerBuffer)) {
            headerBuffer.compact()
            return
        } else {
            state = ReaderState.HEADER
            header(headerBuffer)
        }
    }

    private fun header(headerBuffer: ByteBuffer) {
        if (headerBuffer.remaining() < 20) {
            headerBuffer.compact()
            headerBuffer.limit(20)
            return
        }
        command = getCommand(headerBuffer)
        length = Decode.uint32(headerBuffer).toInt()
        if (length > MAX_PAYLOAD_SIZE) {
            throw NodeException("Payload of " + length + " bytes received, no more than " +
                MAX_PAYLOAD_SIZE + " was expected.")
        }
        headerBuffer.get(checksum)
        state = ReaderState.DATA
        this.headerBuffer = null
        BufferPool.deallocate(headerBuffer)
        val dataBuffer = BufferPool.allocate(length)
        this.dataBuffer = dataBuffer
        dataBuffer.clear()
        dataBuffer.limit(length)
        data(dataBuffer)
    }

    private fun data(dataBuffer: ByteBuffer) {
        if (dataBuffer.position() < length) {
            return
        } else {
            dataBuffer.flip()
        }
        if (!testChecksum(dataBuffer)) {
            state = ReaderState.MAGIC
            this.dataBuffer = null
            BufferPool.deallocate(dataBuffer)
            throw NodeException("Checksum failed for message '$command'")
        }
        try {
            V3MessageFactory.getPayload(
                command ?: throw IllegalStateException("command is null"),
                ByteArrayInputStream(dataBuffer.array(),
                    dataBuffer.arrayOffset() + dataBuffer.position(), length),
                length
            )?.let { messages.add(NetworkMessage(it)) }
        } catch (e: IOException) {
            throw NodeException(e.message)
        } finally {
            state = ReaderState.MAGIC
            this.dataBuffer = null
            BufferPool.deallocate(dataBuffer)
        }
    }

    fun getMessages(): MutableList<NetworkMessage> {
        return messages
    }

    private fun findMagicBytes(buffer: ByteBuffer): Boolean {
        var i = 0
        while (buffer.hasRemaining()) {
            if (i == 0) {
                buffer.mark()
            }
            if (buffer.get() == NetworkMessage.MAGIC_BYTES[i]) {
                i++
                if (i == NetworkMessage.MAGIC_BYTES.size) {
                    return true
                }
            } else {
                i = 0
            }
        }
        if (i > 0) {
            buffer.reset()
        }
        return false
    }

    private fun getCommand(buffer: ByteBuffer): String {
        val start = buffer.position()
        var l = 0
        while (l < 12 && buffer.get().toInt() != 0) l++
        var i = l + 1
        while (i < 12) {
            if (buffer.get().toInt() != 0) throw NodeException("'\\u0000' padding expected for command")
            i++
        }
        return String(buffer.array(), start, l, Charsets.US_ASCII)
    }

    private fun testChecksum(buffer: ByteBuffer): Boolean {
        val payloadChecksum = cryptography().sha512(buffer.array(),
            buffer.arrayOffset() + buffer.position(), length)
        for (i in checksum.indices) {
            if (checksum[i] != payloadChecksum[i]) {
                return false
            }
        }
        return true
    }

    /**
     * De-allocates all buffers. This method should be called iff the reader isn't used anymore, i.e. when its
     * connection is severed.
     */
    fun cleanup() {
        state = null
        headerBuffer?.let { BufferPool.deallocate(it) }
        dataBuffer?.let { BufferPool.deallocate(it) }
    }

    private enum class ReaderState {
        MAGIC, HEADER, DATA
    }
}
