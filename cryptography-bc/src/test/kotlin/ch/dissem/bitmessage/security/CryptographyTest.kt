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

package ch.dissem.bitmessage.security

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException
import ch.dissem.bitmessage.ports.MultiThreadedPOWEngine
import ch.dissem.bitmessage.ports.ProofOfWorkEngine
import ch.dissem.bitmessage.utils.*
import ch.dissem.bitmessage.utils.UnixTime.DAY
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import ch.dissem.bitmessage.utils.UnixTime.now
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.bind.DatatypeConverter

/**
 * @author Christian Basler
 */
class CryptographyTest {

    @Test
    fun testRipemd160() {
        assertArrayEquals(TEST_RIPEMD160, crypto.ripemd160(TEST_VALUE))
    }

    @Test
    fun testSha1() {
        assertArrayEquals(TEST_SHA1, crypto.sha1(TEST_VALUE))
    }

    @Test
    fun testSha512() {
        assertArrayEquals(TEST_SHA512, crypto.sha512(TEST_VALUE))
    }

    @Test
    fun testChaining() {
        assertArrayEquals(TEST_SHA512, crypto.sha512("test".toByteArray(), "string".toByteArray()))
    }

    @Test
    fun `ensure double hash yields same result as hash of hash`() {
        assertArrayEquals(crypto.sha512(TEST_SHA512), crypto.doubleSha512(TEST_VALUE))
    }

    @Test(expected = InsufficientProofOfWorkException::class)
    fun `ensure exception for insufficient proof of work`() {
        val objectMessage = ObjectMessage.Builder()
            .nonce(ByteArray(8))
            .expiresTime(UnixTime.now + 28 * DAY)
            .objectType(0)
            .payload(GenericPayload.read(0, 1, ByteArrayInputStream(ByteArray(0)), 0))
            .build()
        crypto.checkProofOfWork(objectMessage, 1000, 1000)
    }

    @Test
    fun `ensure proof of work is calculated correctly`() {
        TestUtils.mockedInternalContext(
            cryptography = crypto,
            proofOfWorkEngine = MultiThreadedPOWEngine()
        )
        val objectMessage = ObjectMessage(
            nonce = ByteArray(8),
            expiresTime = now + 2 * MINUTE,
            type = 0,
            payload = GenericPayload.read(0, 1, ByteArrayInputStream(ByteArray(0)), 0),
            version = 0,
            stream = 1
        )
        val waiter = CallbackWaiter<ByteArray>()
        crypto.doProofOfWork(objectMessage, 1000, 1000,
            object : ProofOfWorkEngine.Callback {
                override fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray) {
                    waiter.setValue(nonce)
                }
            })
        objectMessage.nonce = waiter.waitForValue()
        try {
            crypto.checkProofOfWork(objectMessage, 1000, 1000)
        } catch (e: InsufficientProofOfWorkException) {
            fail(e.message)
        }
    }

    @Test
    fun `ensure encryption and decryption works`() {
        val data = crypto.randomBytes(100)
        val key_e = crypto.randomBytes(32)
        val iv = crypto.randomBytes(16)
        val encrypted = crypto.crypt(true, data, key_e, iv)
        val decrypted = crypto.crypt(false, encrypted, key_e, iv)
        assertArrayEquals(data, decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ensure decryption fails with invalid cypher text`() {
        val data = crypto.randomBytes(128)
        val key_e = crypto.randomBytes(32)
        val iv = crypto.randomBytes(16)
        crypto.crypt(false, data, key_e, iv)
    }

    @Test
    fun `ensure multiplication works correctly`() {
        val a = crypto.randomBytes(PrivateKey.PRIVATE_KEY_SIZE)
        val A = crypto.createPublicKey(a)

        val b = crypto.randomBytes(PrivateKey.PRIVATE_KEY_SIZE)
        val B = crypto.createPublicKey(b)

        assertArrayEquals(crypto.multiply(A, b), crypto.multiply(B, a))
    }

    @Test
    fun `ensure signature is valid`() {
        val data = crypto.randomBytes(100)
        val privateKey = PrivateKey(false, 1, 1000, 1000)
        val signature = crypto.getSignature(data, privateKey)
        assertThat(crypto.isSignatureValid(data, signature, privateKey.pubkey), `is`(true))
    }

    @Test
    fun `ensure signature is invalid for tempered data`() {
        val data = crypto.randomBytes(100)
        val privateKey = PrivateKey(false, 1, 1000, 1000)
        val signature = crypto.getSignature(data, privateKey)
        data[0]++
        assertThat(crypto.isSignatureValid(data, signature, privateKey.pubkey), `is`(false))
    }

    companion object {
        val TEST_VALUE = "teststring".toByteArray()
        val TEST_SHA1 = DatatypeConverter.parseHexBinary(""
            + "b8473b86d4c2072ca9b08bd28e373e8253e865c4")
        val TEST_SHA512 = DatatypeConverter.parseHexBinary(""
            + "6253b39071e5df8b5098f59202d414c37a17d6a38a875ef5f8c7d89b0212b028"
            + "692d3d2090ce03ae1de66c862fa8a561e57ed9eb7935ce627344f742c0931d72")
        val TEST_RIPEMD160 = DatatypeConverter.parseHexBinary(""
            + "cd566972b5e50104011a92b59fa8e0b1234851ae")

        private val crypto = BouncyCryptography()

        init {
            Singleton.initialize(crypto)
        }
    }
}
