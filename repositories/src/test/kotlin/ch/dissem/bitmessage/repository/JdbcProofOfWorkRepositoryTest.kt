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

package ch.dissem.bitmessage.repository

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.payload.GetPubkey
import ch.dissem.bitmessage.ports.AddressRepository
import ch.dissem.bitmessage.ports.MessageRepository
import ch.dissem.bitmessage.ports.ProofOfWorkRepository.Item
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.TestUtils
import ch.dissem.bitmessage.utils.UnixTime
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

/**
 * @author Christian Basler
 */
class JdbcProofOfWorkRepositoryTest : TestBase() {
    private var config: TestJdbcConfig by Delegates.notNull<TestJdbcConfig>()
    private var repo: JdbcProofOfWorkRepository by Delegates.notNull<JdbcProofOfWorkRepository>()
    private var addressRepo: AddressRepository by Delegates.notNull<AddressRepository>()
    private var messageRepo: MessageRepository by Delegates.notNull<MessageRepository>()

    private var initialHash1: ByteArray by Delegates.notNull<ByteArray>()
    private var initialHash2: ByteArray by Delegates.notNull<ByteArray>()

    @BeforeEach
    fun setUp() {
        config = TestJdbcConfig()
        config.reset()

        addressRepo = JdbcAddressRepository(config)
        messageRepo = JdbcMessageRepository(config)
        repo = JdbcProofOfWorkRepository(config)
        TestUtils.mockedInternalContext(
            addressRepository = addressRepo,
            messageRepository = messageRepo,
            proofOfWorkRepository = repo,
            cryptography = cryptography()
        )

        repo.putObject(
            ObjectMessage.Builder()
                .payload(GetPubkey(BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn"))).build(),
            1000, 1000
        )
        initialHash1 = repo.getItems()[0]

        val sender = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        val recipient = TestUtils.loadContact()
        addressRepo.save(sender)
        addressRepo.save(recipient)
        val plaintext = Plaintext.Builder(MSG)
            .ackData(cryptography().randomBytes(32))
            .from(sender)
            .to(recipient)
            .message("Subject", "Message")
            .status(Plaintext.Status.DOING_PROOF_OF_WORK)
            .build()
        messageRepo.save(plaintext)
        initialHash2 = cryptography().getInitialHash(plaintext.ackMessage!!)
        repo.putObject(
            Item(
                plaintext.ackMessage!!,
                1000, 1000,
                UnixTime.now + 10 * MINUTE,
                plaintext
            )
        )
    }

    @Test
    fun `ensure object is stored`() {
        val sizeBefore = repo.getItems().size
        repo.putObject(
            ObjectMessage.Builder()
                .payload(GetPubkey(BitmessageAddress("BM-2D9U2hv3YBMHM1zERP32anKfVKohyPN9x2"))).build(),
            1000, 1000
        )
        assertEquals(sizeBefore + 1, repo.getItems().size)
    }

    @Test
    fun `ensure ack objects are stored`() {
        val sizeBefore = repo.getItems().size
        val sender = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
        val recipient = TestUtils.loadContact()
        addressRepo.save(sender)
        addressRepo.save(recipient)
        val plaintext = Plaintext.Builder(MSG)
            .ackData(cryptography().randomBytes(32))
            .from(sender)
            .to(recipient)
            .message("Subject", "Message")
            .status(Plaintext.Status.DOING_PROOF_OF_WORK)
            .build()
        messageRepo.save(plaintext)
        repo.putObject(
            Item(
                plaintext.ackMessage!!,
                1000, 1000,
                UnixTime.now + 10 * MINUTE,
                plaintext
            )
        )
        assertEquals(sizeBefore + 1, repo.getItems().size)
    }

    @Test
    fun `ensure item can be retrieved`() {
        val item = repo.getItem(initialHash1)
        assertTrue(item.objectMessage.payload is GetPubkey)
        assertEquals(1000L, item.nonceTrialsPerByte)
        assertEquals(1000L, item.extraBytes)
    }

    @Test
    fun `ensure ack item can be retrieved`() {
        val item = repo.getItem(initialHash2)
        assertTrue(item.objectMessage.payload is GenericPayload)
        assertEquals(1000L, item.nonceTrialsPerByte)
        assertEquals(1000L, item.extraBytes)
        assertTrue(item.expirationTime ?: 0L > 0L)
        assertNotNull(item.message)
        assertNotNull(item.message?.from?.privateKey)
        assertNotNull(item.message?.to?.pubkey)
    }

    @Test
    fun `ensure retrieving nonexisting item causes exception`() {
        assertThrows(RuntimeException::class.java) {
            repo.getItem(ByteArray(0))
        }
    }

    @Test
    fun `ensure item can be deleted`() {
        repo.removeObject(initialHash1)
        repo.removeObject(initialHash2)
        assertTrue(repo.getItems().isEmpty())
    }

    @Test
    fun `ensure deletion of nonexisting item is handled silently`() {
        repo.removeObject(ByteArray(0))
    }
}
