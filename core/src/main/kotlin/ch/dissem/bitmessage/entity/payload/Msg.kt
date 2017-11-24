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

import ch.dissem.bitmessage.entity.Encrypted
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.PlaintextHolder
import ch.dissem.bitmessage.entity.SignedStreamableWriter
import ch.dissem.bitmessage.exception.DecryptionFailedException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Used for person-to-person messages.
 */
class Msg : ObjectPayload, Encrypted, PlaintextHolder {

    override val stream: Long
    private var encrypted: CryptoBox?
    override var plaintext: Plaintext?
        private set

    private constructor(stream: Long, encrypted: CryptoBox) : super(1) {
        this.stream = stream
        this.encrypted = encrypted
        this.plaintext = null
    }

    constructor(plaintext: Plaintext) : super(1) {
        this.stream = plaintext.stream
        this.encrypted = null
        this.plaintext = plaintext
    }

    override val type: ObjectType = ObjectType.MSG

    override val isSigned: Boolean = true

    override var signature: ByteArray?
        get() = plaintext?.signature
        set(signature) {
            plaintext?.signature = signature ?: throw IllegalStateException("no plaintext data available")
        }

    override fun encrypt(publicKey: ByteArray) {
        this.encrypted = CryptoBox(plaintext ?: throw IllegalStateException("no plaintext data available"), publicKey)
    }

    @Throws(DecryptionFailedException::class)
    override fun decrypt(privateKey: ByteArray) {
        plaintext = Plaintext.read(MSG, encrypted!!.decrypt(privateKey))
    }

    override val isDecrypted: Boolean
        get() = plaintext != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Msg) return false
        if (!super.equals(other)) return false

        return stream == other.stream && (encrypted == other.encrypted || plaintext == other.plaintext)
    }

    override fun hashCode() = stream.toInt()

    override fun writer(): SignedStreamableWriter = Writer(this)

    private class Writer(
        private val item: Msg
    ) : SignedStreamableWriter {

        val encryptedDataWriter = item.encrypted?.writer()

        override fun write(out: OutputStream) {
            encryptedDataWriter?.write(out) ?: throw IllegalStateException("Msg must be signed and encrypted before writing it.")
        }

        override fun write(buffer: ByteBuffer) {
            encryptedDataWriter?.write(buffer) ?: throw IllegalStateException("Msg must be signed and encrypted before writing it.")
        }

        override fun writeBytesToSign(out: OutputStream) {
            item.plaintext?.writer(false)?.write(out) ?: throw IllegalStateException("no plaintext data available")
        }

    }

    companion object {
        val ACK_LENGTH = 32

        @JvmStatic
        fun read(input: InputStream, stream: Long, length: Int) = Msg(stream, CryptoBox.read(input, length))
    }
}
