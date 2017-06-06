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

package ch.dissem.bitmessage.entity.valueobject

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Streamable
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.Decode
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.*
import java.nio.ByteBuffer
import java.util.*

/**
 * Represents a private key. Additional information (stream, version, features, ...) is stored in the accompanying
 * [Pubkey] object.
 */
class PrivateKey : Streamable {

    val privateSigningKey: ByteArray
    val privateEncryptionKey: ByteArray

    val pubkey: Pubkey

    constructor(shorter: Boolean, stream: Long, nonceTrialsPerByte: Long, extraBytes: Long, vararg features: Pubkey.Feature) {
        var privSK: ByteArray
        var pubSK: ByteArray
        var privEK: ByteArray
        var pubEK: ByteArray
        var ripe: ByteArray
        do {
            privSK = cryptography().randomBytes(PRIVATE_KEY_SIZE)
            privEK = cryptography().randomBytes(PRIVATE_KEY_SIZE)
            pubSK = cryptography().createPublicKey(privSK)
            pubEK = cryptography().createPublicKey(privEK)
            ripe = Pubkey.getRipe(pubSK, pubEK)
        } while (ripe[0].toInt() != 0 || shorter && ripe[1].toInt() != 0)
        this.privateSigningKey = privSK
        this.privateEncryptionKey = privEK
        this.pubkey = cryptography().createPubkey(Pubkey.LATEST_VERSION, stream, privateSigningKey, privateEncryptionKey,
            nonceTrialsPerByte, extraBytes, *features)
    }

    constructor(privateSigningKey: ByteArray, privateEncryptionKey: ByteArray, pubkey: Pubkey) {
        this.privateSigningKey = privateSigningKey
        this.privateEncryptionKey = privateEncryptionKey
        this.pubkey = pubkey
    }

    constructor(address: BitmessageAddress, passphrase: String) : this(address.version, address.stream, passphrase)

    constructor(version: Long, stream: Long, passphrase: String) : this(Builder(version, stream, false).seed(passphrase).generate())

    private constructor(builder: Builder) {
        this.privateSigningKey = builder.privSK!!
        this.privateEncryptionKey = builder.privEK!!
        this.pubkey = Factory.createPubkey(builder.version, builder.stream, builder.pubSK!!, builder.pubEK!!,
            InternalContext.NETWORK_NONCE_TRIALS_PER_BYTE, InternalContext.NETWORK_EXTRA_BYTES)
    }

    private class Builder internal constructor(internal val version: Long, internal val stream: Long, internal val shorter: Boolean) {

        internal var seed: ByteArray? = null
        internal var nextNonce: Long = 0

        internal var privSK: ByteArray? = null
        internal var privEK: ByteArray? = null
        internal var pubSK: ByteArray? = null
        internal var pubEK: ByteArray? = null

        internal fun seed(passphrase: String): Builder {
            try {
                seed = passphrase.toByteArray(charset("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                throw ApplicationException(e)
            }

            return this
        }

        internal fun generate(): Builder {
            var signingKeyNonce = nextNonce
            var encryptionKeyNonce = nextNonce + 1
            var ripe: ByteArray
            do {
                privEK = Bytes.truncate(cryptography().sha512(seed!!, Encode.varInt(encryptionKeyNonce)), 32)
                privSK = Bytes.truncate(cryptography().sha512(seed!!, Encode.varInt(signingKeyNonce)), 32)
                pubSK = cryptography().createPublicKey(privSK!!)
                pubEK = cryptography().createPublicKey(privEK!!)
                ripe = cryptography().ripemd160(cryptography().sha512(pubSK!!, pubEK!!))

                signingKeyNonce += 2
                encryptionKeyNonce += 2
            } while (ripe[0].toInt() != 0 || shorter && ripe[1].toInt() != 0)
            nextNonce = signingKeyNonce
            return this
        }
    }

    override fun write(out: OutputStream) {
        Encode.varInt(pubkey.version, out)
        Encode.varInt(pubkey.stream, out)
        val baos = ByteArrayOutputStream()
        pubkey.writeUnencrypted(baos)
        Encode.varInt(baos.size().toLong(), out)
        out.write(baos.toByteArray())
        Encode.varInt(privateSigningKey.size.toLong(), out)
        out.write(privateSigningKey)
        Encode.varInt(privateEncryptionKey.size.toLong(), out)
        out.write(privateEncryptionKey)
    }


    override fun write(buffer: ByteBuffer) {
        Encode.varInt(pubkey.version, buffer)
        Encode.varInt(pubkey.stream, buffer)
        try {
            val baos = ByteArrayOutputStream()
            pubkey.writeUnencrypted(baos)
            Encode.varBytes(baos.toByteArray(), buffer)
        } catch (e: IOException) {
            throw ApplicationException(e)
        }

        Encode.varBytes(privateSigningKey, buffer)
        Encode.varBytes(privateEncryptionKey, buffer)
    }

    companion object {
        @JvmField val PRIVATE_KEY_SIZE = 32

        @JvmStatic fun deterministic(passphrase: String, numberOfAddresses: Int, version: Long, stream: Long, shorter: Boolean): List<PrivateKey> {
            val result = ArrayList<PrivateKey>(numberOfAddresses)
            val builder = Builder(version, stream, shorter).seed(passphrase)
            for (i in 0..numberOfAddresses - 1) {
                builder.generate()
                result.add(PrivateKey(builder))
            }
            return result
        }

        @JvmStatic fun read(`is`: InputStream): PrivateKey {
            val version = Decode.varInt(`is`).toInt()
            val stream = Decode.varInt(`is`)
            val len = Decode.varInt(`is`).toInt()
            val pubkey = Factory.readPubkey(version.toLong(), stream, `is`, len, false) ?: throw ApplicationException("Unknown pubkey version encountered")
            val signingKey = Decode.varBytes(`is`)
            val encryptionKey = Decode.varBytes(`is`)
            return PrivateKey(signingKey, encryptionKey, pubkey)
        }
    }
}
