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
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.extensions.pow.ProofOfWorkRequest
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

import ch.dissem.bitmessage.utils.Singleton.cryptography
import org.junit.Assert.assertEquals

class CryptoCustomMessageTest : TestBase() {
    @Test
    fun `ensure encrypt then decrypt yields same object`() {
        val privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"))
        val sendingIdentity = BitmessageAddress(privateKey)

        val payloadBefore = GenericPayload(0, 1, cryptography().randomBytes(100))
        val messageBefore = CryptoCustomMessage(payloadBefore)
        messageBefore.signAndEncrypt(sendingIdentity, cryptography().createPublicKey(sendingIdentity.publicDecryptionKey))

        val out = ByteArrayOutputStream()
        messageBefore.writer().write(out)
        val input = ByteArrayInputStream(out.toByteArray())

        val customMessage = CustomMessage.read(input, out.size())
        val messageAfter = CryptoCustomMessage.read(customMessage,
                object : CryptoCustomMessage.Reader<GenericPayload> {
                    override fun read(sender: BitmessageAddress, input: InputStream) =
                        GenericPayload.read(0, 1, input, 100)
                })
        val payloadAfter = messageAfter.decrypt(sendingIdentity.publicDecryptionKey)

        assertEquals(payloadBefore, payloadAfter)
    }

    @Test
    fun `test with actual request`() {
        val privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"))
        val sendingIdentity = BitmessageAddress(privateKey)

        val requestBefore = ProofOfWorkRequest(sendingIdentity, cryptography().randomBytes(64),
                ProofOfWorkRequest.Request.CALCULATE)

        val messageBefore = CryptoCustomMessage(requestBefore)
        messageBefore.signAndEncrypt(sendingIdentity, cryptography().createPublicKey(sendingIdentity.publicDecryptionKey))


        val out = ByteArrayOutputStream()
        messageBefore.writer().write(out)
        val input = ByteArrayInputStream(out.toByteArray())

        val customMessage = CustomMessage.read(input, out.size())
        val messageAfter = CryptoCustomMessage.read(customMessage,
                ProofOfWorkRequest.Reader(sendingIdentity))
        val requestAfter = messageAfter.decrypt(sendingIdentity.publicDecryptionKey)

        assertEquals(requestBefore, requestAfter)
    }
}
