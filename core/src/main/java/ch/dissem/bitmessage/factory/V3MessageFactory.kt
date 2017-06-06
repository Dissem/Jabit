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
    fun read(`in`: InputStream): NetworkMessage? {
        findMagic(`in`)
        val command = getCommand(`in`)
        val length = Decode.uint32(`in`).toInt()
        if (length > 1600003) {
            throw NodeException("Payload of $length bytes received, no more than 1600003 was expected.")
        }
        val checksum = Decode.bytes(`in`, 4)

        val payloadBytes = Decode.bytes(`in`, length)

        if (testChecksum(checksum, payloadBytes)) {
            val payload = getPayload(command, ByteArrayInputStream(payloadBytes), length)
            if (payload != null)
                return NetworkMessage(payload)
            else
                return null
        } else {
            throw IOException("Checksum failed for message '$command'")
        }
    }

    @JvmStatic
    fun getPayload(command: String, stream: InputStream, length: Int): MessagePayload? {
        when (command) {
            "version" -> return parseVersion(stream)
            "verack" -> return VerAck()
            "addr" -> return parseAddr(stream)
            "inv" -> return parseInv(stream)
            "getdata" -> return parseGetData(stream)
            "object" -> return readObject(stream, length)
            "custom" -> return readCustom(stream, length)
            else -> {
                LOG.debug("Unknown command: " + command)
                return null
            }
        }
    }

    private fun readCustom(`in`: InputStream, length: Int): MessagePayload {
        return CustomMessage.read(`in`, length)
    }

    @JvmStatic
    fun readObject(`in`: InputStream, length: Int): ObjectMessage {
        val counter = AccessCounter()
        val nonce = Decode.bytes(`in`, 8, counter)
        val expiresTime = Decode.int64(`in`, counter)
        val objectType = Decode.uint32(`in`, counter)
        val version = Decode.varInt(`in`, counter)
        val stream = Decode.varInt(`in`, counter)

        val data = Decode.bytes(`in`, length - counter.length())
        var payload: ObjectPayload
        try {
            val dataStream = ByteArrayInputStream(data)
            payload = Factory.getObjectPayload(objectType, version, stream, dataStream, data.size)
        } catch (e: Exception) {
            if (LOG.isTraceEnabled) {
                LOG.trace("Could not parse object payload - using generic payload instead", e)
                LOG.trace(Strings.hex(data).toString())
            }
            payload = GenericPayload(version, stream, data)
        }

        return ObjectMessage.Builder()
            .nonce(nonce)
            .expiresTime(expiresTime)
            .objectType(objectType)
            .stream(stream)
            .payload(payload)
            .build()
    }

    private fun parseGetData(stream: InputStream): GetData {
        val count = Decode.varInt(stream)
        val inventoryVectors = LinkedList<InventoryVector>()
        for (i in 0..count - 1) {
            inventoryVectors.add(parseInventoryVector(stream))
        }
        return GetData(inventoryVectors)
    }

    private fun parseInv(stream: InputStream): Inv {
        val count = Decode.varInt(stream)
        val inventoryVectors = LinkedList<InventoryVector>()
        for (i in 0..count - 1) {
            inventoryVectors.add(parseInventoryVector(stream))
        }
        return Inv(inventoryVectors)
    }

    private fun parseAddr(stream: InputStream): Addr {
        val count = Decode.varInt(stream)
        val networkAddresses = LinkedList<NetworkAddress>()
        for (i in 0..count - 1) {
            networkAddresses.add(parseAddress(stream, false))
        }
        return Addr(networkAddresses)
    }

    private fun parseVersion(stream: InputStream): Version {
        val version = Decode.int32(stream)
        val services = Decode.int64(stream)
        val timestamp = Decode.int64(stream)
        val addrRecv = parseAddress(stream, true)
        val addrFrom = parseAddress(stream, true)
        val nonce = Decode.int64(stream)
        val userAgent = Decode.varString(stream)
        val streamNumbers = Decode.varIntList(stream)

        return Version.Builder()
            .version(version)
            .services(services)
            .timestamp(timestamp)
            .addrRecv(addrRecv).addrFrom(addrFrom)
            .nonce(nonce)
            .userAgent(userAgent)
            .streams(*streamNumbers).build()
    }

    private fun parseInventoryVector(stream: InputStream): InventoryVector {
        return InventoryVector(Decode.bytes(stream, 32))
    }

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
        return NetworkAddress.Builder()
            .time(time)
            .stream(streamNumber)
            .services(services)
            .ipv6(ipv6)
            .port(port)
            .build()
    }

    private fun testChecksum(checksum: ByteArray, payload: ByteArray): Boolean {
        val payloadChecksum = cryptography().sha512(payload)
        for (i in checksum.indices) {
            if (checksum[i] != payloadChecksum[i]) {
                return false
            }
        }
        return true
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

    private fun findMagic(`in`: InputStream) {
        var pos = 0
        for (i in 0..1619999) {
            val b = `in`.read().toByte()
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
