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

import ch.dissem.bitmessage.entity.payload.ObjectPayload
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * The 'object' command sends an object that is shared throughout the network.
 */
data class ObjectMessage(
    var nonce: ByteArray? = null,
    val expiresTime: Long,
    val payload: ObjectPayload,
    val type: Long,
    /**
     * The object's version
     */
    val version: Long,
    val stream: Long
) : MessagePayload {

    override val command: MessagePayload.Command = MessagePayload.Command.OBJECT

    constructor(
        nonce: ByteArray? = null,
        expiresTime: Long,
        payload: ObjectPayload,
        stream: Long
    ) : this(
        nonce,
        expiresTime,
        payload,
        payload.type?.number ?: throw IllegalArgumentException("payload must have type defined"),
        payload.version,
        stream
    )

    val inventoryVector: InventoryVector
        get() {
            return InventoryVector(Bytes.truncate(cryptography().doubleSha512(
                nonce ?: throw IllegalStateException("nonce must be set"),
                payloadBytesWithoutNonce
            ), 32))
        }

    private val isEncrypted: Boolean
        get() = payload is Encrypted && !payload.isDecrypted

    val isSigned: Boolean
        get() = payload.isSigned

    private val bytesToSign: ByteArray
        get() {
            try {
                val out = ByteArrayOutputStream()
                writer.writeHeaderWithoutNonce(out)
                payload.writer().writeBytesToSign(out)
                return out.toByteArray()
            } catch (e: IOException) {
                throw ApplicationException(e)
            }
        }

    fun sign(key: PrivateKey) {
        if (payload.isSigned) {
            payload.signature = cryptography().getSignature(bytesToSign, key)
        }
    }

    @Throws(DecryptionFailedException::class)
    fun decrypt(key: PrivateKey) {
        if (payload is Encrypted) {
            payload.decrypt(key.privateEncryptionKey)
        }
    }

    @Throws(DecryptionFailedException::class)
    fun decrypt(privateEncryptionKey: ByteArray) {
        if (payload is Encrypted) {
            payload.decrypt(privateEncryptionKey)
        }
    }

    fun encrypt(publicEncryptionKey: ByteArray) {
        if (payload is Encrypted) {
            payload.encrypt(publicEncryptionKey)
        }
    }

    fun encrypt(publicKey: Pubkey) {
        try {
            if (payload is Encrypted) {
                payload.encrypt(publicKey.encryptionKey)
            }
        } catch (e: IOException) {
            throw ApplicationException(e)
        }

    }

    fun isSignatureValid(pubkey: Pubkey): Boolean {
        if (isEncrypted) throw IllegalStateException("Payload must be decrypted first")
        return cryptography().isSignatureValid(bytesToSign, payload.signature ?: return false, pubkey)
    }

    val payloadBytesWithoutNonce: ByteArray by lazy {
        val out = ByteArrayOutputStream()
        writer.writeHeaderWithoutNonce(out)
        payload.writer().write(out)
        out.toByteArray()
    }

    private val writer = Writer(this)
    override fun writer(): StreamableWriter = writer

    private class Writer(
        private val item: ObjectMessage
    ) : StreamableWriter {

        override fun write(out: OutputStream) {
            out.write(item.nonce ?: ByteArray(8))
            out.write(item.payloadBytesWithoutNonce)
        }

        override fun write(buffer: ByteBuffer) {
            buffer.put(item.nonce ?: ByteArray(8))
            buffer.put(item.payloadBytesWithoutNonce)
        }

        internal fun writeHeaderWithoutNonce(out: OutputStream) {
            Encode.int64(item.expiresTime, out)
            Encode.int32(item.type, out)
            Encode.varInt(item.version, out)
            Encode.varInt(item.stream, out)
        }

    }

    class Builder {
        private var nonce: ByteArray? = null
        private var expiresTime: Long = 0
        private var objectType: Long? = null
        private var streamNumber: Long = 0
        private var payload: ObjectPayload? = null

        fun nonce(nonce: ByteArray): Builder {
            this.nonce = nonce
            return this
        }

        fun expiresTime(expiresTime: Long): Builder {
            this.expiresTime = expiresTime
            return this
        }

        fun objectType(objectType: Long): Builder {
            this.objectType = objectType
            return this
        }

        fun objectType(objectType: ObjectType): Builder {
            this.objectType = objectType.number
            return this
        }

        fun stream(streamNumber: Long): Builder {
            this.streamNumber = streamNumber
            return this
        }

        fun payload(payload: ObjectPayload): Builder {
            this.payload = payload
            if (this.objectType == null)
                this.objectType = payload.type?.number
            return this
        }

        fun build(): ObjectMessage {
            return ObjectMessage(
                nonce = nonce,
                expiresTime = expiresTime,
                type = objectType!!,
                version = payload!!.version,
                stream = if (streamNumber > 0) streamNumber else payload!!.stream,
                payload = payload!!
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectMessage) return false
        return expiresTime == other.expiresTime &&
            type == other.type &&
            version == other.version &&
            stream == other.stream &&
            payload == other.payload
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(nonce)
        result = 31 * result + (expiresTime xor expiresTime.ushr(32)).toInt()
        result = 31 * result + (type xor type.ushr(32)).toInt()
        result = 31 * result + (version xor version.ushr(32)).toInt()
        result = 31 * result + (stream xor stream.ushr(32)).toInt()
        result = 31 * result + (payload.hashCode())
        return result
    }
}
