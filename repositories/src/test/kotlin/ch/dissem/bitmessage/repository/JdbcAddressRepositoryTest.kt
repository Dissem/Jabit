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

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbcAddressRepositoryTest : TestBase() {
    private val CONTACT_A = "BM-2cW7cD5cDQJDNkE7ibmyTxfvGAmnPqa9Vt"
    private val CONTACT_B = "BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj"
    private val CONTACT_C = "BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke"

    private lateinit var IDENTITY_A: String
    private lateinit var IDENTITY_B: String

    private lateinit var config: TestJdbcConfig
    private lateinit var repo: JdbcAddressRepository

    @BeforeEach
    fun setUp() {
        config = TestJdbcConfig()
        config.reset()

        repo = JdbcAddressRepository(config)

        repo.save(BitmessageAddress(CONTACT_A))
        repo.save(BitmessageAddress(CONTACT_B))
        repo.save(BitmessageAddress(CONTACT_C))

        val identityA = BitmessageAddress(PrivateKey(false, 1, 1000, 1000, DOES_ACK))
        repo.save(identityA)
        IDENTITY_A = identityA.address
        val identityB = BitmessageAddress(PrivateKey(false, 1, 1000, 1000))
        repo.save(identityB)
        IDENTITY_B = identityB.address
    }

    @Test
    fun `ensure contact can be found`() {
        val address = BitmessageAddress(CONTACT_A)
        assertEquals(4, address.version)
        assertEquals(address, repo.findContact(address.tag!!))
        assertNull(repo.findIdentity(address.tag!!))
    }

    @Test
    fun `ensure identity can be found`() {
        val identity = BitmessageAddress(IDENTITY_A)
        assertEquals(4, identity.version)
        assertNull(repo.findContact(identity.tag!!))

        val storedIdentity = repo.findIdentity(identity.tag!!)
        assertEquals(identity, storedIdentity)
        assertTrue(storedIdentity!!.has(DOES_ACK))
    }

    @Test
    fun `ensure identities are retrieved`() {
        val identities = repo.getIdentities()
        assertEquals(2, identities.size.toLong())
        for (identity in identities) {
            assertNotNull(identity.privateKey)
        }
    }

    @Test
    fun `ensure subscriptions are retrieved`() {
        addSubscription("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        addSubscription("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")
        addSubscription("BM-2D9QKN4teYRvoq2fyzpiftPh9WP9qggtzh")
        val subscriptions = repo.getSubscriptions()
        assertEquals(3, subscriptions.size.toLong())
    }

    @Test
    fun `ensure subscriptions are retrieved for given version`() {
        addSubscription("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        addSubscription("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")
        addSubscription("BM-2D9QKN4teYRvoq2fyzpiftPh9WP9qggtzh")

        var subscriptions = repo.getSubscriptions(5)

        assertEquals(1, subscriptions.size.toLong())

        subscriptions = repo.getSubscriptions(4)
        assertEquals(2, subscriptions.size.toLong())
    }

    @Test
    fun `ensure contacts are retrieved`() {
        val contacts = repo.getContacts()
        assertEquals(3, contacts.size.toLong())
        for (contact in contacts) {
            assertNull(contact.privateKey)
        }
    }

    @Test
    fun `ensure new address is saved`() {
        repo.save(BitmessageAddress(PrivateKey(false, 1, 1000, 1000)))
        val identities = repo.getIdentities()
        assertEquals(3, identities.size.toLong())
    }

    @Test
    fun `ensure existing address is updated`() {
        var address = repo.getAddress(CONTACT_A)
        address!!.alias = "Test-Alias"
        repo.save(address)
        address = repo.getAddress(address.address)
        assertEquals("Test-Alias", address!!.alias)
    }

    @Test
    fun `ensure existing keys are not deleted`() {
        val address = BitmessageAddress(IDENTITY_A)
        address.alias = "Test"
        repo.save(address)
        val identityA = repo.getAddress(IDENTITY_A)
        assertNotNull(identityA!!.pubkey)
        assertNotNull(identityA.privateKey)
        assertEquals("Test", identityA.alias)
        assertFalse(identityA.isChan)
    }

    @Test
    fun `ensure new chan is saved and updated`() {
        val chan = BitmessageAddress.chan(1, "test")
        repo.save(chan)
        var address = repo.getAddress(chan.address)
        assertNotNull(address)
        assertTrue(address!!.isChan)

        address.alias = "Test"
        repo.save(address)

        address = repo.getAddress(chan.address)
        assertNotNull(address)
        assertTrue(address!!.isChan)
        assertEquals("Test", address.alias)
    }

    @Test
    fun `ensure address is removed`() {
        val address = repo.getAddress(IDENTITY_A)
        repo.remove(address!!)
        assertNull(repo.getAddress(IDENTITY_A))
    }

    @Test
    fun `ensure address can be retrieved`() {
        val address = repo.getAddress(IDENTITY_A)
        assertNotNull(address)
        assertNotNull(address!!.privateKey)
    }

    private fun addSubscription(address: String) {
        val subscription = BitmessageAddress(address)
        subscription.isSubscribed = true
        repo.save(subscription)
    }
}
