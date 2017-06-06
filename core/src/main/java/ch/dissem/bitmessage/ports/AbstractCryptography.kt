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

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.UnixTime
import ch.dissem.bitmessage.utils.max
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implements everything that isn't directly dependent on either Spongy- or Bouncycastle.
 */
abstract class AbstractCryptography protected constructor(@JvmField protected val provider: Provider) : Cryptography {
    private val context by InternalContext

    @JvmField protected val ALGORITHM_ECDSA = "ECDSA"
    @JvmField protected val ALGORITHM_ECDSA_SHA1 = "SHA1withECDSA"
    @JvmField protected val ALGORITHM_EVP_SHA256 = "SHA256withECDSA"

    override fun sha512(data: ByteArray, offset: Int, length: Int): ByteArray {
        val mda = md("SHA-512")
        mda.update(data, offset, length)
        return mda.digest()
    }

    override fun sha512(vararg data: ByteArray): ByteArray {
        return hash("SHA-512", *data)
    }

    override fun doubleSha512(vararg data: ByteArray): ByteArray {
        val mda = md("SHA-512")
        for (d in data) {
            mda.update(d)
        }
        return mda.digest(mda.digest())
    }

    override fun doubleSha512(data: ByteArray, length: Int): ByteArray {
        val mda = md("SHA-512")
        mda.update(data, 0, length)
        return mda.digest(mda.digest())
    }

    override fun ripemd160(vararg data: ByteArray): ByteArray {
        return hash("RIPEMD160", *data)
    }

    override fun doubleSha256(data: ByteArray, length: Int): ByteArray {
        val mda = md("SHA-256")
        mda.update(data, 0, length)
        return mda.digest(mda.digest())
    }

    override fun sha1(vararg data: ByteArray): ByteArray {
        return hash("SHA-1", *data)
    }

    override fun randomBytes(length: Int): ByteArray {
        val result = ByteArray(length)
        RANDOM.nextBytes(result)
        return result
    }

    override fun doProofOfWork(`object`: ObjectMessage, nonceTrialsPerByte: Long,
                               extraBytes: Long, callback: ProofOfWorkEngine.Callback) {

        val initialHash = getInitialHash(`object`)

        val target = getProofOfWorkTarget(`object`,
            max(nonceTrialsPerByte, NETWORK_NONCE_TRIALS_PER_BYTE), max(extraBytes, NETWORK_EXTRA_BYTES))

        context.proofOfWorkEngine.calculateNonce(initialHash, target, callback)
    }

    @Throws(InsufficientProofOfWorkException::class)
    override fun checkProofOfWork(`object`: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long) {
        val target = getProofOfWorkTarget(`object`, nonceTrialsPerByte, extraBytes)
        val value = doubleSha512(`object`.nonce ?: throw ApplicationException("Object without nonce"), getInitialHash(`object`))
        if (Bytes.lt(target, value, 8)) {
            throw InsufficientProofOfWorkException(target, value)
        }
    }

    @Throws(GeneralSecurityException::class)
    protected fun doSign(data: ByteArray, privKey: java.security.PrivateKey): ByteArray {
        // TODO: change this to ALGORITHM_EVP_SHA256 once it's generally used in the network
        val sig = Signature.getInstance(ALGORITHM_ECDSA_SHA1, provider)
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }


    @Throws(GeneralSecurityException::class)
    protected fun doCheckSignature(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        for (algorithm in arrayOf(ALGORITHM_ECDSA_SHA1, ALGORITHM_EVP_SHA256)) {
            val sig = Signature.getInstance(algorithm, provider)
            sig.initVerify(publicKey)
            sig.update(data)
            if (sig.verify(signature)) {
                return true
            }
        }
        return false
    }

    override fun getInitialHash(`object`: ObjectMessage): ByteArray {
        return sha512(`object`.payloadBytesWithoutNonce)
    }

    override fun getProofOfWorkTarget(`object`: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long): ByteArray {
        @Suppress("NAME_SHADOWING")
        val nonceTrialsPerByte = if (nonceTrialsPerByte == 0L) NETWORK_NONCE_TRIALS_PER_BYTE else nonceTrialsPerByte
        @Suppress("NAME_SHADOWING")
        val extraBytes = if (extraBytes == 0L) NETWORK_EXTRA_BYTES else extraBytes

        val TTL = BigInteger.valueOf(`object`.expiresTime - UnixTime.now)
        val powLength = BigInteger.valueOf(`object`.payloadBytesWithoutNonce.size + extraBytes)
        val denominator = BigInteger.valueOf(nonceTrialsPerByte)
            .multiply(
                powLength.add(
                    powLength.multiply(TTL).divide(TWO_POW_16)
                )
            )
        return Bytes.expand(TWO_POW_64.divide(denominator).toByteArray(), 8)
    }

    private fun hash(algorithm: String, vararg data: ByteArray): ByteArray {
        val mda = md(algorithm)
        for (d in data) {
            mda.update(d)
        }
        return mda.digest()
    }

    private fun md(algorithm: String): MessageDigest {
        try {
            return MessageDigest.getInstance(algorithm, provider)
        } catch (e: GeneralSecurityException) {
            throw ApplicationException(e)
        }

    }

    override fun mac(key_m: ByteArray, data: ByteArray): ByteArray {
        try {
            val mac = Mac.getInstance("HmacSHA256", provider)
            mac.init(SecretKeySpec(key_m, "HmacSHA256"))
            return mac.doFinal(data)
        } catch (e: GeneralSecurityException) {
            throw ApplicationException(e)
        }

    }

    override fun createPubkey(version: Long, stream: Long, privateSigningKey: ByteArray, privateEncryptionKey: ByteArray,
                              nonceTrialsPerByte: Long, extraBytes: Long, vararg features: Pubkey.Feature): Pubkey {
        return Factory.createPubkey(version, stream,
            createPublicKey(privateSigningKey),
            createPublicKey(privateEncryptionKey),
            nonceTrialsPerByte, extraBytes, *features)
    }

    override fun keyToBigInt(privateKey: ByteArray): BigInteger {
        return BigInteger(1, privateKey)
    }

    override fun randomNonce(): Long {
        return RANDOM.nextLong()
    }

    companion object {
        protected val LOG = LoggerFactory.getLogger(Cryptography::class.java)
        private val RANDOM = SecureRandom()
        private val TWO = BigInteger.valueOf(2)
        private val TWO_POW_64 = TWO.pow(64)
        private val TWO_POW_16 = TWO.pow(16)
    }
}
