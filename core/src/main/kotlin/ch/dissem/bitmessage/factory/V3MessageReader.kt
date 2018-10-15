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
    val buffer: ByteBuffer = ByteBuffer.allocate(MAX_PAYLOAD_SIZE)

    private var state: ReaderState? = ReaderState.MAGIC
    private var command: String? = null
    private var length: Int = 0
    private val checksum = ByteArray(4)

    private val messages = LinkedList<NetworkMessage>()

    fun update() {
        if (state != ReaderState.DATA) {
            buffer.flip()
        }
        var s = when (state) {
            ReaderState.MAGIC -> magic()
            ReaderState.HEADER -> header()
            ReaderState.DATA -> data()
            else -> ReaderState.WAIT_FOR_DATA
        }
        while (s != ReaderState.WAIT_FOR_DATA) {
            s = when (state) {
                ReaderState.MAGIC -> magic()
                ReaderState.HEADER -> header()
                ReaderState.DATA -> data(flip = false)
                else -> ReaderState.WAIT_FOR_DATA
            }
        }
    }

    private fun magic(): ReaderState = if (!findMagicBytes(buffer)) {
        buffer.compact()
        ReaderState.WAIT_FOR_DATA
    } else {
        state = ReaderState.HEADER
        ReaderState.HEADER
    }

    private fun header(): ReaderState {
        if (buffer.remaining() < 20) {
            buffer.compact()
            return ReaderState.WAIT_FOR_DATA
        }
        command = getCommand(buffer)
        length = Decode.uint32(buffer).toInt()
        if (length > MAX_PAYLOAD_SIZE) {
            throw NodeException(
                "Payload of " + length + " bytes received, no more than " +
                    MAX_PAYLOAD_SIZE + " was expected."
            )
        }
        buffer.get(checksum)
        state = ReaderState.DATA
        return ReaderState.DATA
    }

    private fun data(flip: Boolean = true): ReaderState {
        if (flip) {
            if (buffer.position() < length) {
                return ReaderState.WAIT_FOR_DATA
            } else {
                buffer.flip()
            }
        } else if (buffer.remaining() < length) {
            buffer.compact()
            return ReaderState.WAIT_FOR_DATA
        }
        if (!testChecksum(buffer)) {
            state = ReaderState.MAGIC
            buffer.clear()
            throw NodeException("Checksum failed for message '$command'")
        }
        try {
            V3MessageFactory.getPayload(
                command ?: throw IllegalStateException("command is null"),
                ByteArrayInputStream(
                    buffer.array(),
                    buffer.arrayOffset() + buffer.position(), length
                ),
                length
            )?.let { messages.add(NetworkMessage(it)) }
        } catch (e: IOException) {
            throw NodeException(e.message)
        } finally {
            state = ReaderState.MAGIC
        }
        return ReaderState.MAGIC
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
        val payloadChecksum = cryptography().sha512(
            buffer.array(),
            buffer.arrayOffset() + buffer.position(), length
        )
        for (i in checksum.indices) {
            if (checksum[i] != payloadChecksum[i]) {
                return false
            }
        }
        return true
    }

    private enum class ReaderState {
        MAGIC, HEADER, DATA, WAIT_FOR_DATA
    }
}
