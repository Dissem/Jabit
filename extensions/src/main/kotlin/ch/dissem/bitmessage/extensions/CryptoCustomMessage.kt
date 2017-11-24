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

package ch.dissem.bitmessage.extensions

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.CustomMessage
import ch.dissem.bitmessage.entity.Streamable
import ch.dissem.bitmessage.entity.StreamableWriter
import ch.dissem.bitmessage.entity.payload.CryptoBox
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.Decode.bytes
import ch.dissem.bitmessage.utils.Decode.int32
import ch.dissem.bitmessage.utils.Decode.varBytes
import ch.dissem.bitmessage.utils.Decode.varInt
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A [CustomMessage] implementation that contains signed and encrypted data.

 * @author Christian Basler
 */
class CryptoCustomMessage<T : Streamable> : CustomMessage {

    private val dataReader: Reader<T>?
    private var container: CryptoBox? = null
    var sender: BitmessageAddress? = null
        private set
    private var data: T? = null
        private set

    constructor(data: T) : super(COMMAND, null) {
        this.data = data
        this.dataReader = null
    }

    private constructor(container: CryptoBox, dataReader: Reader<T>) : super(COMMAND, null) {
        this.container = container
        this.dataReader = dataReader
    }

    fun signAndEncrypt(identity: BitmessageAddress, publicKey: ByteArray) {
        val out = ByteArrayOutputStream()

        val privateKey = identity.privateKey ?: throw IllegalStateException("signing identity must have a private key")

        Encode.varInt(identity.version, out)
        Encode.varInt(identity.stream, out)
        Encode.int32(privateKey.pubkey.behaviorBitfield, out)
        out.write(privateKey.pubkey.signingKey, 1, 64)
        out.write(privateKey.pubkey.encryptionKey, 1, 64)
        if (identity.version >= 3) {
            Encode.varInt(privateKey.pubkey.nonceTrialsPerByte, out)
            Encode.varInt(privateKey.pubkey.extraBytes, out)
        }

        data?.writer()?.write(out) ?: throw IllegalStateException("no unencrypted data available")
        Encode.varBytes(cryptography().getSignature(out.toByteArray(), privateKey), out)
        container = CryptoBox(out.toByteArray(), publicKey)
    }

    @Throws(DecryptionFailedException::class)
    fun decrypt(privateKey: ByteArray): T {
        val input = SignatureCheckingInputStream(container?.decrypt(privateKey) ?: throw IllegalStateException("no encrypted data available"))
        if (dataReader == null) throw IllegalStateException("no data reader available")

        val addressVersion = varInt(input)
        val stream = varInt(input)
        val behaviorBitfield = int32(input)
        val publicSigningKey = bytes(input, 64)
        val publicEncryptionKey = bytes(input, 64)
        val nonceTrialsPerByte = if (addressVersion >= 3) varInt(input) else 0
        val extraBytes = if (addressVersion >= 3) varInt(input) else 0

        val sender = BitmessageAddress(Factory.createPubkey(
            addressVersion,
            stream,
            publicSigningKey,
            publicEncryptionKey,
            nonceTrialsPerByte,
            extraBytes,
            behaviorBitfield
        ))
        this.sender = sender

        data = dataReader.read(sender, input)

        input.checkSignature(sender.pubkey!!)

        return data!!
    }

    override fun writer(): StreamableWriter = Writer(this)

    private class Writer(
        private val item: CryptoCustomMessage<*>
    ) : CustomMessage.Writer(item) {

        override fun write(out: OutputStream) {
            Encode.varString(COMMAND, out)
            item.container?.writer()?.write(out) ?: throw IllegalStateException("not encrypted yet")
        }

    }

    interface Reader<out T> {
        fun read(sender: BitmessageAddress, input: InputStream): T
    }

    private inner class SignatureCheckingInputStream internal constructor(private val wrapped: InputStream) : InputStream() {
        private val out = ByteArrayOutputStream()

        override fun read(): Int {
            val read = wrapped.read()
            if (read >= 0) out.write(read)
            return read
        }

        @Throws(IllegalStateException::class)
        fun checkSignature(pubkey: Pubkey) {
            if (!cryptography().isSignatureValid(out.toByteArray(), varBytes(wrapped), pubkey)) {
                throw IllegalStateException("Signature check failed")
            }
        }
    }

    companion object {
        @JvmField
        val COMMAND = "ENCRYPTED"

        @JvmStatic
        fun <T : Streamable> read(data: CustomMessage, dataReader: Reader<T>): CryptoCustomMessage<T> {
            val cryptoBox = CryptoBox.read(ByteArrayInputStream(data.getData()), data.getData().size)
            return CryptoCustomMessage(cryptoBox, dataReader)
        }
    }
}
