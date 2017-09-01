/*
 * Copyright 2016 Christian Basler
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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK
import ch.dissem.bitmessage.networking.nio.NioNetworkHandler
import ch.dissem.bitmessage.ports.DefaultLabeler
import ch.dissem.bitmessage.ports.Labeler
import ch.dissem.bitmessage.repository.*
import ch.dissem.bitmessage.utils.TTL
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.timeout
import com.nhaarman.mockito_kotlin.verify
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

class SystemTest {
    private var port = 6000

    private lateinit var alice: BitmessageContext
    private lateinit var aliceIdentity: BitmessageAddress
    private lateinit var aliceLabeler: Labeler

    private lateinit var bob: BitmessageContext
    private lateinit var bobListener: TestListener
    private lateinit var bobIdentity: BitmessageAddress

    @Before
    fun setUp() {
        TTL.msg = 5 * MINUTE
        TTL.getpubkey = 5 * MINUTE
        TTL.pubkey = 5 * MINUTE

        val alicePort = port++
        val bobPort = port++
        run {
            val aliceDB = JdbcConfig("jdbc:h2:mem:alice;DB_CLOSE_DELAY=-1", "sa", "")
            aliceLabeler = spy(DebugLabeler("Alice"))
            val aliceListener = TestListener()
            alice = BitmessageContext.Builder()
                .addressRepo(JdbcAddressRepository(aliceDB))
                .inventory(JdbcInventory(aliceDB))
                .messageRepo(JdbcMessageRepository(aliceDB))
                .powRepo(JdbcProofOfWorkRepository(aliceDB))
                .port(alicePort)
                .nodeRegistry(TestNodeRegistry(bobPort))
                .networkHandler(NioNetworkHandler())
                .cryptography(BouncyCryptography())
                .listener(aliceListener)
                .labeler(aliceLabeler)
                .build()
            alice.startup()
            aliceIdentity = alice.createIdentity(false, DOES_ACK)
        }
        run {
            val bobDB = JdbcConfig("jdbc:h2:mem:bob;DB_CLOSE_DELAY=-1", "sa", "")
            bobListener = TestListener()
            bob = BitmessageContext.Builder()
                .addressRepo(JdbcAddressRepository(bobDB))
                .inventory(JdbcInventory(bobDB))
                .messageRepo(JdbcMessageRepository(bobDB))
                .powRepo(JdbcProofOfWorkRepository(bobDB))
                .port(bobPort)
                .nodeRegistry(TestNodeRegistry(alicePort))
                .networkHandler(NioNetworkHandler())
                .cryptography(BouncyCryptography())
                .listener(bobListener)
                .labeler(DebugLabeler("Bob"))
                .build()
            bob.startup()
            bobIdentity = bob.createIdentity(false, DOES_ACK)
        }
        (alice.labeler as DebugLabeler).init(aliceIdentity, bobIdentity)
        (bob.labeler as DebugLabeler).init(aliceIdentity, bobIdentity)
    }

    @After
    fun tearDown() {
        alice.shutdown()
        bob.shutdown()
    }

    @Test(timeout = 120_000)
    fun ensureAliceCanSendMessageToBob() {
        val originalMessage = UUID.randomUUID().toString()
        alice.send(aliceIdentity, BitmessageAddress(bobIdentity.address), "Subject", originalMessage)

        val plaintext = bobListener[2, TimeUnit.MINUTES]

        assertThat(plaintext.type, equalTo(Plaintext.Type.MSG))
        assertThat(plaintext.text, equalTo(originalMessage))

        //TODO: Something must be of here because the 'anyOrNull' matcher does not seem necessary but without it the test fails
        verify(aliceLabeler, timeout(TimeUnit.MINUTES.toMillis(2)).atLeastOnce()).markAsAcknowledged(anyOrNull())
    }

    @Test(timeout = 30_000)
    fun ensureBobCanReceiveBroadcastFromAlice() {
        val originalMessage = UUID.randomUUID().toString()
        bob.addSubscribtion(BitmessageAddress(aliceIdentity.address))
        alice.broadcast(aliceIdentity, "Subject", originalMessage)

        val plaintext = bobListener[15, TimeUnit.MINUTES]

        assertThat(plaintext.type, equalTo(Plaintext.Type.BROADCAST))
        assertThat(plaintext.text, equalTo(originalMessage))
    }

    private class DebugLabeler constructor(internal val name: String) : DefaultLabeler() {
        private val LOG = LoggerFactory.getLogger("Labeler")
        internal lateinit var alice: String
        internal lateinit var bob: String

        fun init(alice: BitmessageAddress, bob: BitmessageAddress) {
            this.alice = alice.address
            this.bob = bob.address
        }

        override fun setLabels(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Received")
            super.setLabels(msg)
        }

        override fun markAsDraft(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Draft")
            super.markAsDraft(msg)
        }

        override fun markAsSending(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Sending")
            super.markAsSending(msg)
        }

        override fun markAsSent(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Sent")
            super.markAsSent(msg)
        }

        override fun markAsAcknowledged(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Acknowledged")
            super.markAsAcknowledged(msg)
        }

        override fun markAsRead(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Read")
            super.markAsRead(msg)
        }

        override fun markAsUnread(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Unread")
            super.markAsUnread(msg)
        }

        override fun delete(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Cleared")
            super.delete(msg)
        }

        override fun archive(msg: Plaintext) {
            LOG.info(name + ": From " + name(msg.from) + ": Archived")
            super.archive(msg)
        }

        private fun name(address: BitmessageAddress): String {
            return when {
                alice == address.address -> "Alice"
                bob == address.address -> "Bob"
                else -> "Unknown (" + address.address + ")"
            }
        }
    }
}
