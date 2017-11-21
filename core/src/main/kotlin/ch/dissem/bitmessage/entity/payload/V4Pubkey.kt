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

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Encrypted
import ch.dissem.bitmessage.entity.EncryptedStreamableWriter
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.utils.Decode
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * A version 4 public key. When version 4 pubkeys are created, most of the data in the pubkey is encrypted. This is
 * done in such a way that only someone who has the Bitmessage address which corresponds to a pubkey can decrypt and
 * use that pubkey. This prevents people from gathering pubkeys sent around the network and using the data from them
 * to create messages to be used in spam or in flooding attacks.
 */
class V4Pubkey : Pubkey, Encrypted {

    override val stream: Long
    val tag: ByteArray
    private var encrypted: CryptoBox? = null
    private var decrypted: V3Pubkey? = null

    private constructor(stream: Long, tag: ByteArray, encrypted: CryptoBox) : super(4) {
        this.stream = stream
        this.tag = tag
        this.encrypted = encrypted
    }

    constructor(decrypted: V3Pubkey) : super(4) {
        this.stream = decrypted.stream
        this.decrypted = decrypted
        this.tag = BitmessageAddress.calculateTag(4, decrypted.stream, decrypted.ripe)
    }

    override fun encrypt(publicKey: ByteArray) {
        if (signature == null) throw IllegalStateException("Pubkey must be signed before encryption.")
        this.encrypted = CryptoBox(decrypted ?: throw IllegalStateException("no plaintext pubkey data available"), publicKey)
    }

    @Throws(DecryptionFailedException::class)
    override fun decrypt(privateKey: ByteArray) {
        decrypted = V3Pubkey.read(encrypted?.decrypt(privateKey) ?: throw IllegalStateException("no encrypted data available"), stream)
    }

    override val isDecrypted: Boolean
        get() = decrypted != null

    override val signingKey: ByteArray
        get() = decrypted?.signingKey ?: throw IllegalStateException("pubkey is encrypted")

    override val encryptionKey: ByteArray
        get() = decrypted?.encryptionKey ?: throw IllegalStateException("pubkey is encrypted")

    override val behaviorBitfield: Int
        get() = decrypted?.behaviorBitfield ?: throw IllegalStateException("pubkey is encrypted")

    override var signature: ByteArray?
        get() = decrypted?.signature
        set(signature) {
            decrypted?.signature = signature
        }

    override val isSigned: Boolean = true

    override val nonceTrialsPerByte: Long
        get() = decrypted?.nonceTrialsPerByte ?: throw IllegalStateException("pubkey is encrypted")

    override val extraBytes: Long
        get() = decrypted?.extraBytes ?: throw IllegalStateException("pubkey is encrypted")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is V4Pubkey) return false

        if (stream != other.stream) return false
        if (!Arrays.equals(tag, other.tag)) return false
        return !if (decrypted != null) decrypted != other.decrypted else other.decrypted != null

    }

    override fun hashCode(): Int {
        var result = (stream xor stream.ushr(32)).toInt()
        result = 31 * result + Arrays.hashCode(tag)
        result = 31 * result + if (decrypted != null) decrypted!!.hashCode() else 0
        return result
    }

    override fun writer(): EncryptedStreamableWriter = Writer(this)

    private class Writer(
        val item: V4Pubkey
    ) : EncryptedStreamableWriter {

        override fun write(out: OutputStream) {
            out.write(item.tag)
            item.encrypted?.writer()?.write(out) ?: throw IllegalStateException("pubkey is encrypted")
        }

        override fun write(buffer: ByteBuffer) {
            buffer.put(item.tag)
            item.encrypted?.writer()?.write(buffer) ?: throw IllegalStateException("pubkey is encrypted")
        }

        override fun writeUnencrypted(out: OutputStream) {
            item.decrypted?.writer()?.write(out) ?: throw IllegalStateException("pubkey is encrypted")
        }

        override fun writeUnencrypted(buffer: ByteBuffer) {
            item.decrypted?.writer()?.write(buffer) ?: throw IllegalStateException("pubkey is encrypted")
        }

        override fun writeBytesToSign(out: OutputStream) {
            out.write(item.tag)
            item.decrypted?.writer()?.writeBytesToSign(out) ?: throw IllegalStateException("pubkey is encrypted")
        }

    }

    companion object {
        @JvmStatic
        fun read(`in`: InputStream, stream: Long, length: Int, encrypted: Boolean): V4Pubkey {
            if (encrypted)
                return V4Pubkey(stream,
                    Decode.bytes(`in`, 32),
                    CryptoBox.read(`in`, length - 32))
            else
                return V4Pubkey(V3Pubkey.read(`in`, stream))
        }
    }
}
