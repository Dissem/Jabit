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

import ch.dissem.bitmessage.entity.*
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.payload.ObjectPayload
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.utils.AccessCounter
import ch.dissem.bitmessage.utils.Decode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.Strings
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Creates protocol v3 network messages from [InputStreams][InputStream]
 */
object V3MessageFactory {
    private val LOG = LoggerFactory.getLogger(V3MessageFactory::class.java)

    @JvmStatic
    fun read(input: InputStream): NetworkMessage? {
        findMagic(input)
        val command = getCommand(input)
        val length = Decode.uint32(input).toInt()
        if (length > 1600003) {
            throw NodeException("Payload of $length bytes received, no more than 1600003 was expected.")
        }
        val checksum = Decode.bytes(input, 4)

        val payloadBytes = Decode.bytes(input, length)

        if (testChecksum(checksum, payloadBytes)) {
            val payload = getPayload(command, ByteArrayInputStream(payloadBytes), length)
            return payload?.let { NetworkMessage(payload) }
        } else {
            throw IOException("Checksum failed for message '$command'")
        }
    }

    @JvmStatic
    fun getPayload(command: String, stream: InputStream, length: Int): MessagePayload? = when (command) {
        "version" -> parseVersion(stream)
        "verack" -> VerAck()
        "addr" -> Addr(parseList(stream) { parseAddress(it, false) })
        "inv" -> Inv(parseList(stream) { parseInventoryVector(it) })
        "getdata" -> GetData(parseList(stream) { parseInventoryVector(it) })
        "object" -> readObject(stream, length)
        "custom" -> readCustom(stream, length)
        else -> {
            LOG.debug("Unknown command: $command")
            null
        }
    }

    private fun readCustom(input: InputStream, length: Int): MessagePayload = CustomMessage.read(input, length)

    @JvmStatic
    fun readObject(input: InputStream, length: Int): ObjectMessage {
        val counter = AccessCounter()
        val nonce = Decode.bytes(input, 8, counter)
        val expiresTime = Decode.int64(input, counter)
        val objectType = Decode.uint32(input, counter)
        val version = Decode.varInt(input, counter)
        val stream = Decode.varInt(input, counter)

        val data = Decode.bytes(input, length - counter.length())
        val payload: ObjectPayload = try {
            Factory.getObjectPayload(objectType, version, stream, ByteArrayInputStream(data), data.size)
        } catch (e: Exception) {
            if (LOG.isTraceEnabled) {
                LOG.trace("Could not parse object payload - using generic payload instead", e)
                LOG.trace(Strings.hex(data))
            }
            GenericPayload(version, stream, data)
        }

        return ObjectMessage(
            nonce, expiresTime, payload, objectType, version, stream
        )
    }

    private fun <T> parseList(stream: InputStream, reader: (InputStream) -> (T)): List<T> {
        val count = Decode.varInt(stream)
        val items = LinkedList<T>()
        for (i in 0 until count) {
            items.add(reader(stream))
        }
        return items
    }

    private fun parseVersion(stream: InputStream) = Version(
        version = Decode.int32(stream),
        services = Decode.int64(stream),
        timestamp = Decode.int64(stream),
        addrRecv = parseAddress(stream, true),
        addrFrom = parseAddress(stream, true),
        nonce = Decode.int64(stream),
        userAgent = Decode.varString(stream),
        streams = Decode.varIntList(stream)
    )

    private fun parseInventoryVector(stream: InputStream) = InventoryVector(Decode.bytes(stream, 32))

    private fun parseAddress(stream: InputStream, light: Boolean): NetworkAddress {
        val time: Long
        val streamNumber: Long
        if (!light) {
            time = Decode.int64(stream)
            streamNumber = Decode.uint32(stream) // This isn't consistent, not sure if this is correct
        } else {
            time = 0
            streamNumber = 0
        }
        val services = Decode.int64(stream)
        val ipv6 = Decode.bytes(stream, 16)
        val port = Decode.uint16(stream)

        return NetworkAddress(
            time, streamNumber, services, ipv6, port
        )
    }

    private fun testChecksum(checksum: ByteArray, payload: ByteArray): Boolean {
        val payloadChecksum = cryptography().sha512(payload)
        return checksum.indices.none { checksum[it] != payloadChecksum[it] }
    }

    private fun getCommand(stream: InputStream): String {
        val bytes = ByteArray(12)
        var end = bytes.size
        for (i in bytes.indices) {
            bytes[i] = stream.read().toByte()
            if (end == bytes.size) {
                if (bytes[i].toInt() == 0) end = i
            } else {
                if (bytes[i].toInt() != 0) throw IOException("'\\u0000' padding expected for command")
            }
        }
        return String(bytes, 0, end, Charsets.US_ASCII)
    }

    private fun findMagic(input: InputStream) {
        var pos = 0
        for (i in 0..1619999) {
            val b = input.read().toByte()
            if (b == NetworkMessage.MAGIC_BYTES[pos]) {
                if (pos + 1 == NetworkMessage.MAGIC_BYTES.size) {
                    return
                }
            } else if (pos > 0 && b == NetworkMessage.MAGIC_BYTES[0]) {
                pos = 1
            } else {
                pos = 0
            }
            pos++
        }
        throw NodeException("Failed to find MAGIC bytes in stream")
    }
}
