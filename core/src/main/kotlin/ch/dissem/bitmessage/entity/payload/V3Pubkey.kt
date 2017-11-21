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

package ch.dissem.bitmessage.entity.payload

import ch.dissem.bitmessage.entity.EncryptedStreamableWriter
import ch.dissem.bitmessage.entity.SignedStreamableWriter
import ch.dissem.bitmessage.entity.StreamableWriter
import ch.dissem.bitmessage.utils.Decode
import ch.dissem.bitmessage.utils.Encode
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * A version 3 public key.
 */
class V3Pubkey protected constructor(
    version: Long, stream: Long, behaviorBitfield: Int,
    signingKey: ByteArray, encryptionKey: ByteArray,
    override val nonceTrialsPerByte: Long,
    override val extraBytes: Long,
    override var signature: ByteArray? = null
) : V2Pubkey(version, stream, behaviorBitfield, signingKey, encryptionKey) {

    override val isSigned: Boolean = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is V3Pubkey) return false
        return nonceTrialsPerByte == other.nonceTrialsPerByte &&
            extraBytes == other.extraBytes &&
            stream == other.stream &&
            behaviorBitfield == other.behaviorBitfield &&
            Arrays.equals(signingKey, other.signingKey) &&
            Arrays.equals(encryptionKey, other.encryptionKey)
    }

    override fun hashCode(): Int {
        return Objects.hash(nonceTrialsPerByte, extraBytes)
    }

    override fun writer(): EncryptedStreamableWriter = Writer(this)

    protected open class Writer(
        private val item: V3Pubkey
    ) : V2Pubkey.Writer(item) {

        override fun write(out: OutputStream) {
            writeBytesToSign(out)
            Encode.varBytes(
                item.signature ?: throw IllegalStateException("signature not available"),
                out
            )
        }

        override fun write(buffer: ByteBuffer) {
            super.write(buffer)
            Encode.varInt(item.nonceTrialsPerByte, buffer)
            Encode.varInt(item.extraBytes, buffer)
            Encode.varBytes(
                item.signature ?: throw IllegalStateException("signature not available"),
                buffer
            )
        }

        override fun writeBytesToSign(out: OutputStream) {
            super.write(out)
            Encode.varInt(item.nonceTrialsPerByte, out)
            Encode.varInt(item.extraBytes, out)
        }
    }

    class Builder {
        private var streamNumber: Long = 0
        private var behaviorBitfield: Int = 0
        private var publicSigningKey: ByteArray? = null
        private var publicEncryptionKey: ByteArray? = null
        private var nonceTrialsPerByte: Long = 0
        private var extraBytes: Long = 0
        private var signature = ByteArray(0)

        fun stream(streamNumber: Long): Builder {
            this.streamNumber = streamNumber
            return this
        }

        fun behaviorBitfield(behaviorBitfield: Int): Builder {
            this.behaviorBitfield = behaviorBitfield
            return this
        }

        fun publicSigningKey(publicSigningKey: ByteArray): Builder {
            this.publicSigningKey = publicSigningKey
            return this
        }

        fun publicEncryptionKey(publicEncryptionKey: ByteArray): Builder {
            this.publicEncryptionKey = publicEncryptionKey
            return this
        }

        fun nonceTrialsPerByte(nonceTrialsPerByte: Long): Builder {
            this.nonceTrialsPerByte = nonceTrialsPerByte
            return this
        }

        fun extraBytes(extraBytes: Long): Builder {
            this.extraBytes = extraBytes
            return this
        }

        fun signature(signature: ByteArray): Builder {
            this.signature = signature
            return this
        }

        fun build(): V3Pubkey {
            return V3Pubkey(
                version = 3,
                stream = streamNumber,
                behaviorBitfield = behaviorBitfield,
                signingKey = publicSigningKey!!,
                encryptionKey = publicEncryptionKey!!,
                nonceTrialsPerByte = nonceTrialsPerByte,
                extraBytes = extraBytes,
                signature = signature
            )
        }
    }

    companion object {
        @JvmStatic fun read(`is`: InputStream, stream: Long): V3Pubkey {
            return V3Pubkey(
                version = 3,
                stream = stream,
                behaviorBitfield = Decode.int32(`is`),
                signingKey = Decode.bytes(`is`, 64),
                encryptionKey = Decode.bytes(`is`, 64),
                nonceTrialsPerByte = Decode.varInt(`is`),
                extraBytes = Decode.varInt(`is`),
                signature = Decode.varBytes(`is`)
            )
        }
    }
}
