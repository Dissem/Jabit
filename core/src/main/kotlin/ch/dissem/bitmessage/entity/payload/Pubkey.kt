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

import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.EncryptedStreamableWriter
import ch.dissem.bitmessage.entity.SignedStreamableWriter
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

/**
 * Public keys for signing and encryption, the answer to a 'getpubkey' request.
 */
abstract class Pubkey protected constructor(version: Long) : ObjectPayload(version) {

    override val type: ObjectType = ObjectType.PUBKEY

    abstract val signingKey: ByteArray

    abstract val encryptionKey: ByteArray

    abstract val behaviorBitfield: Int

    val ripe: ByteArray by lazy { cryptography().ripemd160(cryptography().sha512(signingKey, encryptionKey)) }

    open val nonceTrialsPerByte: Long = NETWORK_NONCE_TRIALS_PER_BYTE

    open val extraBytes: Long = NETWORK_EXTRA_BYTES

    abstract override fun writer(): EncryptedStreamableWriter

    /**
     * Bits 0 through 29 are yet undefined
     */
    enum class Feature constructor(bitNumber: Int) {
        /**
         * Receiving node expects that the RIPE hash encoded in their address preceedes the encrypted message data of msg
         * messages bound for them.
         */
        INCLUDE_DESTINATION(30),
        /**
         * If true, the receiving node does send acknowledgements (rather than dropping them).
         */
        DOES_ACK(31);

        // The Bitmessage Protocol Specification starts counting at the most significant bit,
        // thus the slightly awkward calculation.
        // https://bitmessage.org/wiki/Protocol_specification#Pubkey_bitfield_features
        private val bit: Int = 1 shl 31 - bitNumber

        fun isActive(bitfield: Int): Boolean {
            return bitfield and bit != 0
        }

        companion object {
            @JvmStatic fun bitfield(vararg features: Feature): Int {
                var bits = 0
                for (feature in features) {
                    bits = bits or feature.bit
                }
                return bits
            }

            @JvmStatic fun features(bitfield: Int): Array<Feature> {
                val features = ArrayList<Feature>(Feature.values().size)
                for (feature in Feature.values()) {
                    if (bitfield and feature.bit != 0) {
                        features.add(feature)
                    }
                }
                return features.toTypedArray()
            }
        }
    }

    companion object {
        @JvmField val LATEST_VERSION: Long = 4

        fun getRipe(publicSigningKey: ByteArray, publicEncryptionKey: ByteArray): ByteArray {
            return cryptography().ripemd160(cryptography().sha512(publicSigningKey, publicEncryptionKey))
        }

        fun add0x04(key: ByteArray): ByteArray {
            if (key.size == 65) return key
            val result = ByteArray(65)
            result[0] = 4
            System.arraycopy(key, 0, result, 1, 64)
            return result
        }
    }
}
