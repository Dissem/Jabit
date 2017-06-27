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

import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature
import ch.dissem.bitmessage.entity.payload.V4Pubkey
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.utils.AccessCounter
import ch.dissem.bitmessage.utils.Base58
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.Decode.bytes
import ch.dissem.bitmessage.utils.Decode.varInt
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.*

/**
 * A Bitmessage address. Can be a user's private address, an address string without public keys or a recipient's address
 * holding private keys.
 */
class BitmessageAddress : Serializable {

    val version: Long
    val stream: Long
    val ripe: ByteArray
    val tag: ByteArray?
    /**
     * The private key used to decrypt Pubkey objects (for v4 addresses) and broadcasts. It's easier to just create
     * it regardless of address version.
     */
    val publicDecryptionKey: ByteArray

    val address: String

    var privateKey: PrivateKey? = null
        private set
    var pubkey: Pubkey? = null
        set(pubkey) {
            if (pubkey != null) {
                if (pubkey is V4Pubkey) {
                    if (!Arrays.equals(tag, pubkey.tag))
                        throw IllegalArgumentException("Pubkey has incompatible tag")
                }
                if (!Arrays.equals(ripe, pubkey.ripe))
                    throw IllegalArgumentException("Pubkey has incompatible ripe")
                field = pubkey
            }
        }


    var alias: String? = null
    var isSubscribed: Boolean = false
    var isChan: Boolean = false

    internal constructor(version: Long, stream: Long, ripe: ByteArray) {
        this.version = version
        this.stream = stream
        this.ripe = ripe

        val os = ByteArrayOutputStream()
        Encode.varInt(version, os)
        Encode.varInt(stream, os)
        if (version < 4) {
            val checksum = cryptography().sha512(os.toByteArray(), ripe)
            this.tag = null
            this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32)
        } else {
            // for tag and decryption key, the checksum has to be created with 0x00 padding
            val checksum = cryptography().doubleSha512(os.toByteArray(), ripe)
            this.tag = Arrays.copyOfRange(checksum, 32, 64)
            this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32)
        }
        // but for the address and its checksum they need to be stripped
        val offset = Bytes.numberOfLeadingZeros(ripe)
        os.write(ripe, offset, ripe.size - offset)
        val checksum = cryptography().doubleSha512(os.toByteArray())
        os.write(checksum, 0, 4)
        this.address = "BM-" + Base58.encode(os.toByteArray())
    }

    constructor(publicKey: Pubkey) : this(publicKey.version, publicKey.stream, publicKey.ripe) {
        this.pubkey = publicKey
    }

    constructor(address: String, passphrase: String) : this(address) {
        val key = PrivateKey(this, passphrase)
        if (!Arrays.equals(ripe, key.pubkey.ripe)) {
            throw IllegalArgumentException("Wrong address or passphrase")
        }
        this.privateKey = key
        this.pubkey = key.pubkey
    }

    constructor(privateKey: PrivateKey) : this(privateKey.pubkey) {
        this.privateKey = privateKey
    }

    constructor(address: String) {
        this.address = address
        val bytes = Base58.decode(address.substring(3))
        val `in` = ByteArrayInputStream(bytes)
        val counter = AccessCounter()
        this.version = varInt(`in`, counter)
        this.stream = varInt(`in`, counter)
        this.ripe = Bytes.expand(bytes(`in`, bytes.size - counter.length() - 4), 20)

        // test checksum
        var checksum = cryptography().doubleSha512(bytes, bytes.size - 4)
        val expectedChecksum = bytes(`in`, 4)
        for (i in 0..3) {
            if (expectedChecksum[i] != checksum[i])
                throw IllegalArgumentException("Checksum of address failed")
        }
        if (version < 4) {
            checksum = cryptography().sha512(Arrays.copyOfRange(bytes, 0, counter.length()), ripe)
            this.tag = null
            this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32)
        } else {
            checksum = cryptography().doubleSha512(Arrays.copyOfRange(bytes, 0, counter.length()), ripe)
            this.tag = Arrays.copyOfRange(checksum, 32, 64)
            this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32)
        }
    }

    override fun toString(): String {
        return alias ?: address
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitmessageAddress) return false
        return version == other.version &&
            stream == other.stream &&
            Arrays.equals(ripe, other.ripe)
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(ripe)
    }

    fun has(feature: Feature?): Boolean {
        return feature?.isActive(pubkey?.behaviorBitfield ?: 0) ?: false
    }

    companion object {
        @JvmStatic fun chan(address: String, passphrase: String): BitmessageAddress {
            val result = BitmessageAddress(address, passphrase)
            result.isChan = true
            return result
        }

        @JvmStatic fun chan(stream: Long, passphrase: String): BitmessageAddress {
            val privateKey = PrivateKey(Pubkey.LATEST_VERSION, stream, passphrase)
            val result = BitmessageAddress(privateKey)
            result.isChan = true
            return result
        }

        @JvmStatic fun deterministic(passphrase: String, numberOfAddresses: Int,
                                     version: Long, stream: Long, shorter: Boolean): List<BitmessageAddress> {
            val result = ArrayList<BitmessageAddress>(numberOfAddresses)
            val privateKeys = PrivateKey.deterministic(passphrase, numberOfAddresses, version, stream, shorter)
            for (pk in privateKeys) {
                result.add(BitmessageAddress(pk))
            }
            return result
        }

        @JvmStatic fun calculateTag(version: Long, stream: Long, ripe: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            Encode.varInt(version, out)
            Encode.varInt(stream, out)
            out.write(ripe)
            return Arrays.copyOfRange(cryptography().doubleSha512(out.toByteArray()), 32, 64)
        }
    }
}
