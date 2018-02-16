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

package ch.dissem.bitmessage

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.ports.Cryptography
import ch.dissem.bitmessage.ports.ProofOfWorkEngine
import ch.dissem.bitmessage.ports.ProofOfWorkRepository
import ch.dissem.bitmessage.utils.Singleton
import ch.dissem.bitmessage.utils.TestUtils
import com.nhaarman.mockito_kotlin.*
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

/**
 * @author Christian Basler
 */
class ProofOfWorkServiceTest {
    private var cryptography by Delegates.notNull<Cryptography>()
    private var ctx by Delegates.notNull<InternalContext>()

    private var obj by Delegates.notNull<ObjectMessage>()

    @Before
    fun setUp() {
        cryptography = spy(BouncyCryptography())
        Singleton.initialize(cryptography)

        ctx = TestUtils.mockedInternalContext(
            cryptography = cryptography
        )

        obj = ObjectMessage(
            expiresTime = 0,
            stream = 1,
            payload = GenericPayload(1, 1, kotlin.ByteArray(0)),
            type = 42,
            version = 42
        )
    }

    @Test
    fun `ensure missing proof of work is done`() {
        whenever(ctx.proofOfWorkRepository.getItems()).thenReturn(Arrays.asList<ByteArray>(ByteArray(64)))
        whenever(ctx.proofOfWorkRepository.getItem(any())).thenReturn(ProofOfWorkRepository.Item(obj, 1001, 1002))
        doNothing().whenever(cryptography).doProofOfWork(any(), any(), any(), any<ProofOfWorkEngine.Callback>())

        ctx.proofOfWorkService.doMissingProofOfWork(10)

        verify(cryptography, timeout(1000)).doProofOfWork(eq(obj), eq(1001L), eq(1002L), any<ProofOfWorkEngine.Callback>())
    }

    @Test
    fun `ensure calculated nonce is stored`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        val address = TestUtils.loadContact()
        val plaintext = Plaintext.Builder(MSG).from(identity).to(address).message("", "").build()
        val objectMessage = ObjectMessage(
            expiresTime = 0,
            stream = 1,
            payload = Msg(plaintext)
        )
        objectMessage.sign(identity.privateKey!!)
        objectMessage.encrypt(address.pubkey!!)
        val initialHash = ByteArray(64)
        val nonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        whenever(ctx.proofOfWorkRepository.getItem(initialHash)).thenReturn(ProofOfWorkRepository.Item(objectMessage, 1001, 1002))
        whenever(ctx.messageRepository.getMessage(initialHash)).thenReturn(plaintext)

        ctx.proofOfWorkService.onNonceCalculated(initialHash, nonce)

        verify(ctx.proofOfWorkRepository).removeObject(eq(initialHash))
        verify(ctx.inventory).storeObject(eq(objectMessage))
        verify(ctx.networkHandler).offer(eq(objectMessage.inventoryVector))
        assertThat(plaintext.inventoryVector, equalTo(objectMessage.inventoryVector))
    }
}
