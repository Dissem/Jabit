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
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Status.PUBKEY_REQUESTED
import ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.GetPubkey
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.ProofOfWorkRepository
import ch.dissem.bitmessage.utils.Singleton
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import ch.dissem.bitmessage.utils.UnixTime.now
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test

/**
 * @author Christian Basler
 */
class DefaultMessageListenerTest : TestBase() {
    private lateinit var listener: DefaultMessageListener

    private val ctx = TestUtils.mockedInternalContext(
        cryptography = BouncyCryptography()
    )

    @Before
    fun setUp() {
        listener = ctx.networkListener as DefaultMessageListener
    }

    @Test
    fun `ensure pubkey is sent on request`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        whenever(ctx.addressRepository.findIdentity(any())).thenReturn(identity)
        val objectMessage = ObjectMessage(
            stream = 2,
            payload = GetPubkey(BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")),
            expiresTime = now + MINUTE
        )
        whenever(ctx.proofOfWorkRepository.getItem(any())).thenReturn(ProofOfWorkRepository.Item(objectMessage, 1000L, 1000L))
        listener.receive(objectMessage)
        verify(ctx.proofOfWorkRepository).putObject(argThat { type == ObjectType.PUBKEY.number }, any(), any())
    }

    @Test
    fun `ensure incoming pubkey is added to contact`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        val contact = BitmessageAddress(identity.address)
        whenever(ctx.addressRepository.findContact(isA())).thenReturn(contact)
        whenever(ctx.messageRepository.findMessages(eq(PUBKEY_REQUESTED), eq(contact)))
            .thenReturn(listOf(Plaintext.Builder(MSG).from(identity).to(contact).message("S", "T").build()))

        val objectMessage = ObjectMessage.Builder()
            .stream(2)
            .payload(identity.pubkey!!)
            .build()
        objectMessage.sign(identity.privateKey!!)
        objectMessage.encrypt(Singleton.cryptography().createPublicKey(identity.publicDecryptionKey))
        whenever(ctx.proofOfWorkRepository.getItem(any())).thenReturn(ProofOfWorkRepository.Item(objectMessage, 1000L, 1000L))
        listener.receive(objectMessage)

        verify(ctx.addressRepository).save(eq(contact))
    }

    @Test
    fun `ensure incoming message is saved`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        val contact = BitmessageAddress(identity.address)
        contact.pubkey = identity.pubkey

        whenever(ctx.addressRepository.getIdentities()).thenReturn(listOf(identity))

        val objectMessage = ObjectMessage.Builder()
            .stream(2)
            .payload(Msg(Plaintext.Builder(MSG)
                .from(identity)
                .to(contact)
                .message("S", "T")
                .build()))
            .nonce(ByteArray(8))
            .build()
        objectMessage.sign(identity.privateKey!!)
        objectMessage.encrypt(identity.pubkey!!)

        listener.receive(objectMessage)

        verify(ctx.messageRepository, atLeastOnce()).save(argThat<Plaintext> { type == MSG })
    }

    @Test
    fun `ensure incoming broadcast is saved`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")

        whenever(ctx.addressRepository.getSubscriptions(any())).thenReturn(listOf(identity))

        val broadcast = Factory.getBroadcast(Plaintext.Builder(BROADCAST)
            .from(identity)
            .message("S", "T")
            .build())
        val objectMessage = ObjectMessage.Builder()
            .stream(2)
            .payload(broadcast)
            .nonce(ByteArray(8))
            .build()
        objectMessage.sign(identity.privateKey!!)
        broadcast.encrypt()

        listener.receive(objectMessage)

        verify(ctx.messageRepository, atLeastOnce()).save(argThat<Plaintext> { type == BROADCAST })
    }
}
