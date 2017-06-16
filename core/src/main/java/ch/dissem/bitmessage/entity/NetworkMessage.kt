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

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * A network message is exchanged between two nodes.
 */
data class NetworkMessage(
    /**
     * The actual data, a message or an object. Not to be confused with objectPayload.
     */
    val payload: MessagePayload
) : Streamable {

    /**
     * First 4 bytes of sha512(payload)
     */
    private fun getChecksum(bytes: ByteArray): ByteArray {
        val d = cryptography().sha512(bytes)
        return byteArrayOf(d[0], d[1], d[2], d[3])
    }

    @Throws(IOException::class)
    override fun write(out: OutputStream) {
        // magic
        Encode.int32(MAGIC, out)

        // ASCII string identifying the packet content, NULL padded (non-NULL padding results in packet rejected)
        val command = payload.command.name.toLowerCase()
        out.write(command.toByteArray(charset("ASCII")))
        for (i in command.length..11) {
            out.write(0x0)
        }

        val payloadBytes = Encode.bytes(payload)

        // Length of payload in number of bytes. Because of other restrictions, there is no reason why this length would
        // ever be larger than 1600003 bytes. Some clients include a sanity-check to avoid processing messages which are
        // larger than this.
        Encode.int32(payloadBytes.size, out)

        // checksum
        out.write(getChecksum(payloadBytes))

        // message payload
        out.write(payloadBytes)
    }

    /**
     * A more efficient implementation of the write method, writing header data to the provided buffer and returning
     * a new buffer containing the payload.

     * @param headerBuffer where the header data is written to (24 bytes)
     * *
     * @return a buffer containing the payload, ready to be read.
     */
    fun writeHeaderAndGetPayloadBuffer(headerBuffer: ByteBuffer): ByteBuffer {
        return ByteBuffer.wrap(writeHeader(headerBuffer))
    }

    /**
     * For improved memory efficiency, you should use [.writeHeaderAndGetPayloadBuffer]
     * and write the header buffer as well as the returned payload buffer into the channel.

     * @param buffer where everything gets written to. Needs to be large enough for the whole message
     * *               to be written.
     */
    override fun write(buffer: ByteBuffer) {
        val payloadBytes = writeHeader(buffer)
        buffer.put(payloadBytes)
    }

    private fun writeHeader(out: ByteBuffer): ByteArray {
        // magic
        Encode.int32(MAGIC, out)

        // ASCII string identifying the packet content, NULL padded (non-NULL padding results in packet rejected)
        val command = payload.command.name.toLowerCase()
        out.put(command.toByteArray(charset("ASCII")))

        for (i in command.length..11) {
            out.put(0.toByte())
        }

        val payloadBytes = Encode.bytes(payload)

        // Length of payload in number of bytes. Because of other restrictions, there is no reason why this length would
        // ever be larger than 1600003 bytes. Some clients include a sanity-check to avoid processing messages which are
        // larger than this.
        Encode.int32(payloadBytes.size, out)

        // checksum
        out.put(getChecksum(payloadBytes))

        // message payload
        return payloadBytes
    }

    companion object {
        /**
         * Magic value indicating message origin network, and used to seek to next message when stream state is unknown
         */
        val MAGIC = 0xE9BEB4D9.toInt()
        val MAGIC_BYTES = ByteBuffer.allocate(4).putInt(MAGIC).array()
    }
}
