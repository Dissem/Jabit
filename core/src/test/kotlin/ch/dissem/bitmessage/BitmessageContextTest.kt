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
import ch.dissem.bitmessage.entity.Plaintext.Type
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.ports.DefaultLabeler
import ch.dissem.bitmessage.ports.ProofOfWorkEngine
import ch.dissem.bitmessage.ports.ProofOfWorkRepository
import ch.dissem.bitmessage.testutils.TestInventory
import ch.dissem.bitmessage.utils.Property
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.Strings.hex
import ch.dissem.bitmessage.utils.TTL
import ch.dissem.bitmessage.utils.TestUtils
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import com.nhaarman.mockito_kotlin.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.concurrent.thread

/**
 * @author Christian Basler
 */
class BitmessageContextTest {
    private var testListener: BitmessageContext.Listener = mock()
    private val testInventory = spy(TestInventory())
    private val testPowRepo = spy(object : ProofOfWorkRepository {
        internal var items: MutableMap<InventoryVector, ProofOfWorkRepository.Item> = HashMap()
        internal var added = 0
        internal var removed = 0

        override fun getItem(initialHash: ByteArray): ProofOfWorkRepository.Item {
            return items[InventoryVector(initialHash)]
                ?: throw IllegalArgumentException("${hex(initialHash)} not found in $items")
        }

        override fun getItems(): List<ByteArray> {
            val result = LinkedList<ByteArray>()
            for ((hash) in items.keys) {
                result.add(hash)
            }
            return result
        }

        override fun putObject(item: ProofOfWorkRepository.Item) {
            items.put(InventoryVector(cryptography().getInitialHash(item.objectMessage)), item)
            added++
        }

        override fun putObject(objectMessage: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long) {
            items.put(
                InventoryVector(cryptography().getInitialHash(objectMessage)),
                ProofOfWorkRepository.Item(objectMessage, nonceTrialsPerByte, extraBytes)
            )
            added++
        }

        override fun removeObject(initialHash: ByteArray) {
            if (items.remove(InventoryVector(initialHash)) != null) {
                removed++
            }
        }

        fun reset() {
            items.clear()
            added = 0
            removed = 0
        }
    })
    private val testPowEngine = spy(object : ProofOfWorkEngine {
        override fun calculateNonce(initialHash: ByteArray, target: ByteArray, callback: ProofOfWorkEngine.Callback) {
            thread { callback.onNonceCalculated(initialHash, ByteArray(8)) }
        }
    })
    private var ctx = BitmessageContext.build {
        addressRepo = mock()
        cryptography = BouncyCryptography()
        inventory = testInventory
        listener = testListener
        labelRepo = mock()
        messageRepo = mock()
        networkHandler = mock {
            on { getNetworkStatus() } doReturn Property("test", "mocked")
        }
        nodeRegistry = mock()
        labeler = spy(DefaultLabeler())
        proofOfWorkRepo = testPowRepo
        proofOfWorkEngine = testPowEngine
    }

    init {
        TTL.msg = 2 * MINUTE
    }

    @BeforeEach
    fun setUp() {
        testPowRepo.reset()
    }

    @Test
    fun `ensure contact is saved and pubkey requested`() {
        val contact = BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT")
        doReturn(contact).whenever(ctx.addresses).getAddress(eq(contact.address))

        ctx.addContact(contact)

        verify(ctx.addresses, timeout(1000).atLeastOnce()).save(eq(contact))
        verify(testPowEngine, timeout(1000)).calculateNonce(any(), any(), any<ProofOfWorkEngine.Callback>())
    }

    @Test
    fun `ensure pubkey is not requested if it exists`() {
        val (_, _, payload) = TestUtils.loadObjectMessage(2, "V2Pubkey.payload")
        val pubkey = payload as Pubkey
        val contact = BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT")
        contact.pubkey = pubkey

        ctx.addContact(contact)

        verify(ctx.addresses, times(1)).save(contact)
        verify(testPowEngine, never()).calculateNonce(any(), any(), any<ProofOfWorkEngine.Callback>())
    }

    @Test
    fun `ensure V2Pubkey is not requested if it exists in inventory`() {
        testInventory.init(
            "V1Msg.payload",
            "V2GetPubkey.payload",
            "V2Pubkey.payload",
            "V3GetPubkey.payload",
            "V3Pubkey.payload",
            "V4Broadcast.payload",
            "V4GetPubkey.payload",
            "V4Pubkey.payload",
            "V5Broadcast.payload"
        )
        val contact = BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT")

        whenever(ctx.addresses.getAddress(contact.address)).thenReturn(contact)

        ctx.addContact(contact)

        verify(ctx.addresses, atLeastOnce()).save(contact)
        verify(testPowEngine, never()).calculateNonce(any(), any(), any<ProofOfWorkEngine.Callback>())
    }

    @Test
    fun `ensure V4Pubkey is not requested if it exists in inventory`() {
        testInventory.init(
            "V1Msg.payload",
            "V2GetPubkey.payload",
            "V2Pubkey.payload",
            "V3GetPubkey.payload",
            "V3Pubkey.payload",
            "V4Broadcast.payload",
            "V4GetPubkey.payload",
            "V4Pubkey.payload",
            "V5Broadcast.payload"
        )
        val contact = BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        val stored = BitmessageAddress(contact.address)
        stored.alias = "Test"
        whenever(ctx.addresses.getAddress(contact.address)).thenReturn(stored)

        ctx.addContact(contact)

        verify(ctx.addresses, atLeastOnce()).save(any())
        verify(testPowEngine, never()).calculateNonce(any(), any(), any<ProofOfWorkEngine.Callback>())
    }

    @Test
    fun `ensure subscription is added and existing broadcasts retrieved`() {
        val address = BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")

        testInventory.init(
            "V4Broadcast.payload",
            "V5Broadcast.payload"
        )

        whenever(ctx.addresses.getSubscriptions(any())).thenReturn(listOf(address))
        ctx.addSubscribtion(address)

        verify(ctx.addresses, atLeastOnce()).save(address)
        assertTrue(address.isSubscribed)
        verify(ctx.internals.inventory).getObjects(eq(address.stream), any(), any())
        verify(testListener).receive(any())
    }

    @Test
    fun `ensure identity is created`() {
        assertNotNull(ctx.createIdentity(false))
    }

    @Test
    fun `ensure message is sent`() {
        ctx.send(
            TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"), TestUtils.loadContact(),
            "Subject", "Message"
        )
        verify(ctx.internals.proofOfWorkRepository, timeout(10000)).putObject(
            argThat { payload.type == ObjectType.MSG }, eq(1000L), eq(1000L)
        )
        assertEquals(2, testPowRepo.added)
        verify(ctx.messages, timeout(10000).atLeastOnce()).save(argThat<Plaintext> { type == Type.MSG })
    }

    @Test
    fun `ensure pubkey is requested if it is missing`() {
        ctx.send(
            TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"),
            BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
            "Subject", "Message"
        )
        verify(testPowRepo, timeout(10000).atLeastOnce())
            .putObject(argThat { payload.type == ObjectType.GET_PUBKEY }, eq(1000L), eq(1000L))
        verify(ctx.messages, timeout(10000).atLeastOnce()).save(argThat<Plaintext> { type == Type.MSG })
    }

    @Test
    fun `ensure sender must be identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            ctx.send(
                BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
                BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
                "Subject", "Message"
            )
        }
    }

    @Test
    fun `ensure broadcast is sent`() {
        ctx.broadcast(
            TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"),
            "Subject", "Message"
        )
        verify(ctx.internals.proofOfWorkRepository, timeout(1000).atLeastOnce())
            .putObject(argThat { payload.type == ObjectType.BROADCAST }, eq(1000L), eq(1000L))
        verify(testPowEngine).calculateNonce(any(), any(), any<ProofOfWorkEngine.Callback>())
        verify(ctx.messages, timeout(10000).atLeastOnce()).save(argThat<Plaintext> { type == Type.BROADCAST })
    }

    @Test
    fun `ensure sender without private key throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            val msg = Plaintext.Builder(Type.BROADCAST)
                .from(BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
                .message("Subject", "Message")
                .build()
            ctx.send(msg)
        }
    }

    @Test
    fun `ensure chan is joined`() {
        val chanAddress = "BM-2cW67GEKkHGonXKZLCzouLLxnLym3azS8r"
        val chan = ctx.joinChan("general", chanAddress)
        assertNotNull(chan)
        assertEquals(chan.address, chanAddress)
        assertTrue(chan.isChan)
    }

    @Test
    fun `ensure deterministic addresses are created`() {
        val expected_size = 8
        val addresses = ctx.createDeterministicAddresses("test", expected_size, 4, 1, false)
        assertEquals(expected_size, addresses.size)
        val expected = HashSet<String>(expected_size)
        expected.add("BM-2cWFkyuXXFw6d393RGnin2RpSXj8wxtt6F")
        expected.add("BM-2cX8TF9vuQZEWvT7UrEeq1HN9dgiSUPLEN")
        expected.add("BM-2cUzX8f9CKUU7L8NeB8GExZvf54PrcXq1S")
        expected.add("BM-2cU7MAoQd7KE8SPF7AKFPpoEZKjk86KRqE")
        expected.add("BM-2cVm8ByVBacc2DVhdTNs6rmy5ZQK6DUsrt")
        expected.add("BM-2cW2af1vB6kWon2WkygDHqGwfcpfAFm2Jk")
        expected.add("BM-2cWdWD7UtUN4gWChgNX9pvyvNPjUZvU8BT")
        expected.add("BM-2cXkYgYcUrv4fGxSHzyEScW955Cc8sDteo")
        for (a in addresses) {
            assertTrue(expected.contains(a.address))
            expected.remove(a.address)
        }
    }

    @Test
    fun `ensure short deterministic addresses are created`() {
        val expected_size = 1
        val addresses = ctx.createDeterministicAddresses("test", expected_size, 4, 1, true)
        assertEquals(expected_size, addresses.size)
        val expected = HashSet<String>(expected_size)
        expected.add("BM-NBGyBAEp6VnBkFWKpzUSgxuTqVdWPi78")
        for (a in addresses) {
            assertTrue(expected.contains(a.address))
            expected.remove(a.address)
        }
    }

    @Test
    fun `ensure chan is created`() {
        val chan = ctx.createChan("test")
        assertNotNull(chan)
        assertEquals(chan.version, Pubkey.LATEST_VERSION)
        assertTrue(chan.isChan)
    }

    @Test
    fun `ensure unacknowledged message is resent`() {
        val plaintext = Plaintext.Builder(Type.MSG)
            .ttl(1)
            .message("subject", "message")
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .build()
        assertTrue(plaintext.to!!.has(Pubkey.Feature.DOES_ACK))
        whenever(ctx.messages.findMessagesToResend()).thenReturn(listOf(plaintext))
        whenever(ctx.messages.getMessage(any<ByteArray>())).thenReturn(plaintext)
        ctx.resendUnacknowledgedMessages()
        verify(ctx.labeler, timeout(1000).times(1)).markAsSent(eq(plaintext))
    }

    @Test
    fun `ensure status contains user agent`() {
        val userAgent = ctx.status().getProperty("user agent")?.value.toString()
        assertEquals("/Jabit:${BitmessageContext.version}/", userAgent)
    }
}
