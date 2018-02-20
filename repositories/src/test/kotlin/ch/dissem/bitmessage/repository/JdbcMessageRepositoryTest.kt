/*
 * Copyright 2015 Christian Basler
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

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.ports.LabelRepository
import ch.dissem.bitmessage.ports.MessageRepository
import ch.dissem.bitmessage.utils.TestUtils
import ch.dissem.bitmessage.utils.TestUtils.mockedInternalContext
import ch.dissem.bitmessage.utils.UnixTime
import org.hamcrest.BaseMatcher
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class JdbcMessageRepositoryTest : TestBase() {
    private lateinit var contactA: BitmessageAddress
    private lateinit var contactB: BitmessageAddress
    private lateinit var identity: BitmessageAddress

    private lateinit var repo: MessageRepository
    private lateinit var labelRepo: LabelRepository

    private lateinit var inbox: Label
    private lateinit var sent: Label
    private lateinit var drafts: Label
    private lateinit var unread: Label

    @BeforeEach
    fun setUp() {
        val config = TestJdbcConfig()
        config.reset()
        val addressRepo = JdbcAddressRepository(config)
        repo = JdbcMessageRepository(config)
        labelRepo = JdbcLabelRepository(config)
        mockedInternalContext(
            cryptography = BouncyCryptography(),
            addressRepository = addressRepo,
            messageRepository = repo,
            port = 12345,
            connectionTTL = 10,
            connectionLimit = 10
        )
        val tmp = BitmessageAddress(PrivateKey(false, 1, 1000, 1000, DOES_ACK))
        contactA = BitmessageAddress(tmp.address)
        contactA.pubkey = tmp.pubkey
        addressRepo.save(contactA)
        contactB = BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj")
        addressRepo.save(contactB)

        identity = BitmessageAddress(PrivateKey(false, 1, 1000, 1000, DOES_ACK))
        addressRepo.save(identity)

        inbox = labelRepo.getLabels(Label.Type.INBOX)[0]
        sent = labelRepo.getLabels(Label.Type.SENT)[0]
        drafts = labelRepo.getLabels(Label.Type.DRAFT)[0]
        unread = labelRepo.getLabels(Label.Type.UNREAD)[0]

        addMessage(contactA, identity, Plaintext.Status.RECEIVED, inbox, unread)
        addMessage(identity, contactA, Plaintext.Status.DRAFT, drafts)
        addMessage(identity, contactB, Plaintext.Status.DRAFT, unread)
    }

    @Test
    fun `ensure messages can be found by label`() {
        val messages = repo.findMessages(inbox)
        assertEquals(1, messages.size.toLong())
        val m = messages[0]
        assertEquals(contactA, m.from)
        assertEquals(identity, m.to)
        assertEquals(Plaintext.Status.RECEIVED, m.status)
    }

    @Test
    fun `ensure unread messages can be found for all labels`() {
        val unread = repo.countUnread(null)
        assertThat(unread, `is`(2))
    }

    @Test
    fun `ensure unread messages can be found by label`() {
        val unread = repo.countUnread(inbox)
        assertThat(unread, `is`(1))
    }

    @Test
    fun `ensure message can be retrieved by initial hash`() {
        val initialHash = ByteArray(64)
        val message = repo.findMessages(contactA)[0]
        message.initialHash = initialHash
        repo.save(message)
        val other = repo.getMessage(initialHash)
        assertThat<Plaintext>(other, `is`(message))
    }

    @Test
    fun `ensure ack message can be updated and retrieved`() {
        val initialHash = ByteArray(64)
        val message = repo.findMessages(contactA)[0]
        message.initialHash = initialHash
        val ackMessage = message.ackMessage
        repo.save(message)
        val other = repo.getMessage(initialHash)!!
        assertThat<Plaintext>(other, `is`(message))
        assertThat<ObjectMessage>(other.ackMessage, `is`<ObjectMessage>(ackMessage))
    }

    @Test
    fun `ensure messages can be found by status`() {
        val messages = repo.findMessages(Plaintext.Status.RECEIVED)
        assertEquals(1, messages.size.toLong())
        val m = messages[0]
        assertEquals(contactA, m.from)
        assertEquals(identity, m.to)
        assertEquals(Plaintext.Status.RECEIVED, m.status)
    }

    @Test
    fun `ensure messages can be found by status and recipient`() {
        val messages = repo.findMessages(Plaintext.Status.DRAFT, contactB)
        assertEquals(1, messages.size.toLong())
        val m = messages[0]
        assertEquals(identity, m.from)
        assertEquals(contactB, m.to)
        assertEquals(Plaintext.Status.DRAFT, m.status)
    }

    @Test
    fun `ensure message can be saved`() {
        val message = Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(identity)
            .to(contactA)
            .message("Subject", "Message")
            .status(Plaintext.Status.DOING_PROOF_OF_WORK)
            .build()
        repo.save(message)

        assertNotNull(message.id)

        message.addLabels(inbox)
        repo.save(message)

        val messages = repo.findMessages(Plaintext.Status.DOING_PROOF_OF_WORK)

        assertEquals(1, messages.size.toLong())
        assertNotNull(messages[0].inventoryVector)
    }

    @Test
    fun `ensure message can be updated`() {
        var messages = repo.findMessages(Plaintext.Status.DRAFT, contactA)
        val message = messages[0]
        message.inventoryVector = TestUtils.randomInventoryVector()
        repo.save(message)

        messages = repo.findMessages(Plaintext.Status.DRAFT, contactA)
        assertEquals(1, messages.size.toLong())
        assertNotNull(messages[0].inventoryVector)
    }

    @Test
    fun `ensure message is removed`() {
        val toRemove = repo.findMessages(Plaintext.Status.DRAFT, contactB)[0]
        var messages = repo.findMessages(Plaintext.Status.DRAFT)
        assertEquals(2, messages.size.toLong())
        repo.remove(toRemove)
        messages = repo.findMessages(Plaintext.Status.DRAFT)
        assertThat(messages, hasSize<Plaintext>(1))
    }

    @Test
    fun `ensure unacknowledged messages are found for resend`() {
        val message = Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(identity)
            .to(contactA)
            .message("Subject", "Message")
            .sent(UnixTime.now)
            .status(Plaintext.Status.SENT)
            .ttl(2)
            .build()
        message.updateNextTry()
        assertThat(message.retries, `is`(1))
        assertThat<Long>(message.nextTry, greaterThan(UnixTime.now))
        assertThat<Long>(message.nextTry, lessThanOrEqualTo(UnixTime.now + 2))
        repo.save(message)
        Thread.sleep(4100) // somewhat longer than 2*TTL
        var messagesToResend = repo.findMessagesToResend()
        assertThat(messagesToResend, hasSize<Plaintext>(1))

        message.updateNextTry()
        assertThat(message.retries, `is`(2))
        assertThat<Long>(message.nextTry, greaterThan(UnixTime.now))
        repo.save(message)
        messagesToResend = repo.findMessagesToResend()
        assertThat(messagesToResend, empty<Plaintext>())
    }

    @Test
    fun `ensure parents are saved`() {
        val parent = storeConversation()

        val responses = repo.findResponses(parent)

        assertThat(responses, hasSize<Plaintext>(2))
        assertThat(responses, hasItem(hasMessage("Re: new test", "Nice!")))
        assertThat(responses, hasItem(hasMessage("Re: new test", "PS: it did work!")))
    }

    @Test
    fun `ensure conversation can be retrieved`() {
        val root = storeConversation()
        val conversations = repo.findConversations(inbox)
        assertThat(conversations, hasSize<UUID>(2))
        assertThat(conversations, hasItem(root.conversationId))
    }

    private fun addMessage(from: BitmessageAddress, to: BitmessageAddress, status: Plaintext.Status, vararg labels: Label): Plaintext {
        val content = Message.Builder()
            .subject("Subject")
            .body("Message")
            .build()
        return addMessage(from, to, content, status, *labels)
    }

    private fun addMessage(from: BitmessageAddress, to: BitmessageAddress,
                           content: ExtendedEncoding, status: Plaintext.Status, vararg labels: Label): Plaintext {
        val message = Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(from)
            .to(to)
            .message(content)
            .status(status)
            .labels(Arrays.asList(*labels))
            .build()
        repo.save(message)
        return message
    }

    private fun storeConversation(): Plaintext {
        val older = addMessage(identity, contactA,
            Message.Builder()
                .subject("hey there")
                .body("does it work?")
                .build(),
            Plaintext.Status.SENT, sent)

        val root = addMessage(identity, contactA,
            Message.Builder()
                .subject("new test")
                .body("There's a new test in town!")
                .build(),
            Plaintext.Status.SENT, sent)

        addMessage(contactA, identity,
            Message.Builder()
                .subject("Re: new test")
                .body("Nice!")
                .addParent(root)
                .build(),
            Plaintext.Status.RECEIVED, inbox)

        addMessage(contactA, identity,
            Message.Builder()
                .subject("Re: new test")
                .body("PS: it did work!")
                .addParent(root)
                .addParent(older)
                .build(),
            Plaintext.Status.RECEIVED, inbox)

        return repo.getMessage(root.id!!)
    }

    private fun hasMessage(subject: String?, body: String?): Matcher<Plaintext> {
        return object : BaseMatcher<Plaintext>() {
            override fun describeTo(description: Description) {
                description.appendText("Subject: ").appendText(subject)
                description.appendText(", ")
                description.appendText("Body: ").appendText(body)
            }

            override fun matches(item: Any): Boolean {
                if (item is Plaintext) {
                    if (subject != null && subject != item.subject) {
                        return false
                    }
                    if (body != null && body != item.text) {
                        return false
                    }
                    return true
                } else {
                    return false
                }
            }
        }
    }
}
