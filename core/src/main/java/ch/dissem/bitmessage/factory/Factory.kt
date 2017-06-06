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

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.NetworkMessage
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.payload.*
import ch.dissem.bitmessage.entity.payload.ObjectType.MSG
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.UnixTime
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Creates [NetworkMessage] objects from [InputStreams][InputStream]
 */
object Factory {
    private val LOG = LoggerFactory.getLogger(Factory::class.java)

    @Throws(SocketTimeoutException::class)
    @JvmStatic fun getNetworkMessage(@Suppress("UNUSED_PARAMETER") version: Int, stream: InputStream): NetworkMessage? {
        try {
            return V3MessageFactory.read(stream)
        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException,
                is NodeException -> throw e
                is SocketException -> throw NodeException(e.message, e)
                else -> {
                    LOG.error(e.message, e)
                    return null
                }
            }
        }

    }

    @JvmStatic fun getObjectMessage(version: Int, stream: InputStream, length: Int): ObjectMessage? {
        try {
            return V3MessageFactory.readObject(stream, length)
        } catch (e: IOException) {
            LOG.error(e.message, e)
            return null
        }
    }

    @JvmStatic fun createPubkey(version: Long, stream: Long, publicSigningKey: ByteArray, publicEncryptionKey: ByteArray,
                                nonceTrialsPerByte: Long, extraBytes: Long, vararg features: Pubkey.Feature): Pubkey {
        return createPubkey(version, stream, publicSigningKey, publicEncryptionKey, nonceTrialsPerByte, extraBytes,
            Pubkey.Feature.bitfield(*features))
    }

    @JvmStatic fun createPubkey(version: Long, stream: Long, publicSigningKey: ByteArray, publicEncryptionKey: ByteArray,
                                nonceTrialsPerByte: Long, extraBytes: Long, behaviourBitfield: Int): Pubkey {
        if (publicSigningKey.size != 64 && publicSigningKey.size != 65)
            throw IllegalArgumentException("64 bytes signing key expected, but it was "
                + publicSigningKey.size + " bytes long.")
        if (publicEncryptionKey.size != 64 && publicEncryptionKey.size != 65)
            throw IllegalArgumentException("64 bytes encryption key expected, but it was "
                + publicEncryptionKey.size + " bytes long.")

        when (version.toInt()) {
            2 -> return V2Pubkey.Builder()
                .stream(stream)
                .publicSigningKey(publicSigningKey)
                .publicEncryptionKey(publicEncryptionKey)
                .behaviorBitfield(behaviourBitfield)
                .build()
            3 -> return V3Pubkey.Builder()
                .stream(stream)
                .publicSigningKey(publicSigningKey)
                .publicEncryptionKey(publicEncryptionKey)
                .behaviorBitfield(behaviourBitfield)
                .nonceTrialsPerByte(nonceTrialsPerByte)
                .extraBytes(extraBytes)
                .build()
            4 -> return V4Pubkey(
                V3Pubkey.Builder()
                    .stream(stream)
                    .publicSigningKey(publicSigningKey)
                    .publicEncryptionKey(publicEncryptionKey)
                    .behaviorBitfield(behaviourBitfield)
                    .nonceTrialsPerByte(nonceTrialsPerByte)
                    .extraBytes(extraBytes)
                    .build()
            )
            else -> throw IllegalArgumentException("Unexpected pubkey version " + version)
        }
    }

    @JvmStatic fun createIdentityFromPrivateKey(address: String,
                                                privateSigningKey: ByteArray, privateEncryptionKey: ByteArray,
                                                nonceTrialsPerByte: Long, extraBytes: Long,
                                                behaviourBitfield: Int): BitmessageAddress {
        val temp = BitmessageAddress(address)
        val privateKey = PrivateKey(privateSigningKey, privateEncryptionKey,
            createPubkey(temp.version, temp.stream,
                cryptography().createPublicKey(privateSigningKey),
                cryptography().createPublicKey(privateEncryptionKey),
                nonceTrialsPerByte, extraBytes, behaviourBitfield))
        val result = BitmessageAddress(privateKey)
        if (result.address != address) {
            throw IllegalArgumentException("Address not matching private key. Address: " + address
                + "; Address derived from private key: " + result.address)
        }
        return result
    }

    @JvmStatic fun generatePrivateAddress(shorter: Boolean,
                                          stream: Long,
                                          vararg features: Pubkey.Feature): BitmessageAddress {
        return BitmessageAddress(PrivateKey(shorter, stream, 1000, 1000, *features))
    }

    @JvmStatic fun getObjectPayload(objectType: Long,
                                    version: Long,
                                    streamNumber: Long,
                                    stream: InputStream,
                                    length: Int): ObjectPayload {
        val type = ObjectType.fromNumber(objectType)
        if (type != null) {
            when (type) {
                ObjectType.GET_PUBKEY -> return parseGetPubkey(version, streamNumber, stream, length)
                ObjectType.PUBKEY -> return parsePubkey(version, streamNumber, stream, length)
                MSG -> return parseMsg(version, streamNumber, stream, length)
                ObjectType.BROADCAST -> return parseBroadcast(version, streamNumber, stream, length)
                else -> LOG.error("This should not happen, someone broke something in the code!")
            }
        }
        // fallback: just store the message - we don't really care what it is
        LOG.trace("Unexpected object type: " + objectType)
        return GenericPayload.read(version, streamNumber, stream, length)
    }

    @JvmStatic private fun parseGetPubkey(version: Long, streamNumber: Long, stream: InputStream, length: Int): ObjectPayload {
        return GetPubkey.read(stream, streamNumber, length, version)
    }

    @JvmStatic fun readPubkey(version: Long, stream: Long, `is`: InputStream, length: Int, encrypted: Boolean): Pubkey? {
        when (version.toInt()) {
            2 -> return V2Pubkey.read(`is`, stream)
            3 -> return V3Pubkey.read(`is`, stream)
            4 -> return V4Pubkey.read(`is`, stream, length, encrypted)
        }
        LOG.debug("Unexpected pubkey version $version, handling as generic payload object")
        return null
    }

    @JvmStatic private fun parsePubkey(version: Long, streamNumber: Long, stream: InputStream, length: Int): ObjectPayload {
        val pubkey = readPubkey(version, streamNumber, stream, length, true)
        return pubkey ?: GenericPayload.read(version, streamNumber, stream, length)
    }

    @JvmStatic private fun parseMsg(version: Long, streamNumber: Long, stream: InputStream, length: Int): ObjectPayload {
        return Msg.read(stream, streamNumber, length)
    }

    @JvmStatic private fun parseBroadcast(version: Long, streamNumber: Long, stream: InputStream, length: Int): ObjectPayload {
        when (version.toInt()) {
            4 -> return V4Broadcast.read(stream, streamNumber, length)
            5 -> return V5Broadcast.read(stream, streamNumber, length)
            else -> {
                LOG.debug("Encountered unknown broadcast version " + version)
                return GenericPayload.read(version, streamNumber, stream, length)
            }
        }
    }

    @JvmStatic fun getBroadcast(plaintext: Plaintext): Broadcast {
        val sendingAddress = plaintext.from
        if (sendingAddress.version < 4) {
            return V4Broadcast(sendingAddress, plaintext)
        } else {
            return V5Broadcast(sendingAddress, plaintext)
        }
    }

    @JvmStatic fun createAck(from: BitmessageAddress, ackData: ByteArray?, ttl: Long): ObjectMessage? {
        val ack = GenericPayload(
            3, from.stream,
            ackData ?: return null
        )
        return ObjectMessage.Builder().objectType(MSG).payload(ack).expiresTime(UnixTime.now + ttl).build()
    }
}
