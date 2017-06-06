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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Provides some methods to help with hashing and encryption. All randoms are created using [SecureRandom],
 * which should be secure enough.
 */
interface Cryptography {
    /**
     * A helper method to calculate SHA-512 hashes. Please note that a new [MessageDigest] object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.

     * @param data   to get hashed
     * *
     * @param offset of the data to be hashed
     * *
     * @param length of the data to be hashed
     * *
     * @return SHA-512 hash of data within the given range
     */
    fun sha512(data: ByteArray, offset: Int, length: Int): ByteArray

    /**
     * A helper method to calculate SHA-512 hashes. Please note that a new [MessageDigest] object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.

     * @param data to get hashed
     * *
     * @return SHA-512 hash of data
     */
    fun sha512(vararg data: ByteArray): ByteArray

    /**
     * A helper method to calculate doubleSHA-512 hashes. Please note that a new [MessageDigest] object is created
     * at each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.

     * @param data to get hashed
     * *
     * @return SHA-512 hash of data
     */
    fun doubleSha512(vararg data: ByteArray): ByteArray

    /**
     * A helper method to calculate double SHA-512 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     *
     *
     * Please note that a new [MessageDigest] object is created at each call (to ensure thread safety), so you
     * shouldn't use this if you need to do many hash calculations in short order on the same thread.
     *

     * @param data   to get hashed
     * *
     * @param length number of bytes to be taken into account
     * *
     * @return SHA-512 hash of data
     */
    fun doubleSha512(data: ByteArray, length: Int): ByteArray

    /**
     * A helper method to calculate RIPEMD-160 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     *
     *
     * Please note that a new [MessageDigest] object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     *

     * @param data to get hashed
     * *
     * @return RIPEMD-160 hash of data
     */
    fun ripemd160(vararg data: ByteArray): ByteArray

    /**
     * A helper method to calculate double SHA-256 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     *
     *
     * Please note that a new [MessageDigest] object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     *

     * @param data   to get hashed
     * *
     * @param length number of bytes to be taken into account
     * *
     * @return SHA-256 hash of data
     */
    fun doubleSha256(data: ByteArray, length: Int): ByteArray

    /**
     * A helper method to calculate SHA-1 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     *
     *
     * Please note that a new [MessageDigest] object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     *

     * @param data to get hashed
     * *
     * @return SHA hash of data
     */
    fun sha1(vararg data: ByteArray): ByteArray

    /**
     * @param length number of bytes to return
     * *
     * @return an array of the given size containing random bytes
     */
    fun randomBytes(length: Int): ByteArray

    /**
     * Calculates the proof of work. This might take a long time, depending on the hardware, message size and time to
     * live.

     * @param object             to do the proof of work for
     * *
     * @param nonceTrialsPerByte difficulty
     * *
     * @param extraBytes         bytes to add to the object size (makes it more difficult to send small messages)
     * *
     * @param callback           to handle nonce once it's calculated
     */
    fun doProofOfWork(`object`: ObjectMessage, nonceTrialsPerByte: Long,
                      extraBytes: Long, callback: ProofOfWorkEngine.Callback)

    /**
     * @param object             to be checked
     * *
     * @param nonceTrialsPerByte difficulty
     * *
     * @param extraBytes         bytes to add to the object size
     * *
     * @throws InsufficientProofOfWorkException if proof of work doesn't check out (makes it more difficult to send small messages)
     */
    @Throws(InsufficientProofOfWorkException::class)
    fun checkProofOfWork(`object`: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long)

    fun getInitialHash(`object`: ObjectMessage): ByteArray

    fun getProofOfWorkTarget(`object`: ObjectMessage, nonceTrialsPerByte: Long = NETWORK_NONCE_TRIALS_PER_BYTE, extraBytes: Long = NETWORK_EXTRA_BYTES): ByteArray

    /**
     * Calculates the MAC for a message (data)

     * @param key_m the symmetric key used
     * *
     * @param data  the message data to calculate the MAC for
     * *
     * @return the MAC
     */
    fun mac(key_m: ByteArray, data: ByteArray): ByteArray

    /**
     * @param encrypt if true, encrypts data, otherwise tries to decrypt it.
     * *
     * @param data
     * *
     * @param key_e
     * *
     * @return
     */
    fun crypt(encrypt: Boolean, data: ByteArray, key_e: ByteArray, initializationVector: ByteArray): ByteArray

    /**
     * Create a new public key fom given private keys.

     * @param version              of the public key / address
     * *
     * @param stream               of the address
     * *
     * @param privateSigningKey    private key used for signing
     * *
     * @param privateEncryptionKey private key used for encryption
     * *
     * @param nonceTrialsPerByte   proof of work difficulty
     * *
     * @param extraBytes           bytes to add for the proof of work (make it harder for small messages)
     * *
     * @param features             of the address
     * *
     * @return a public key object
     */
    fun createPubkey(version: Long, stream: Long, privateSigningKey: ByteArray, privateEncryptionKey: ByteArray,
                     nonceTrialsPerByte: Long, extraBytes: Long, vararg features: Pubkey.Feature): Pubkey

    /**
     * @param privateKey private key as byte array
     * *
     * @return a public key corresponding to the given private key
     */
    fun createPublicKey(privateKey: ByteArray): ByteArray

    /**
     * @param privateKey private key as byte array
     * *
     * @return a big integer representation (unsigned) of the given bytes
     */
    fun keyToBigInt(privateKey: ByteArray): BigInteger

    /**
     * @param data      to check
     * *
     * @param signature the signature of the message
     * *
     * @param pubkey    the sender's public key
     * *
     * @return true if the signature is valid, false otherwise
     */
    fun isSignatureValid(data: ByteArray, signature: ByteArray, pubkey: Pubkey): Boolean

    /**
     * Calculate the signature of data, using the given private key.

     * @param data       to be signed
     * *
     * @param privateKey to be used for signing
     * *
     * @return the signature
     */
    fun getSignature(data: ByteArray, privateKey: ch.dissem.bitmessage.entity.valueobject.PrivateKey): ByteArray

    /**
     * @return a random number of type long
     */
    fun randomNonce(): Long

    fun multiply(k: ByteArray, r: ByteArray): ByteArray

    fun createPoint(x: ByteArray, y: ByteArray): ByteArray
}
