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

package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JdbcAddressRepositoryTest extends TestBase {
    public static final String CONTACT_A = "BM-2cW7cD5cDQJDNkE7ibmyTxfvGAmnPqa9Vt";
    public static final String CONTACT_B = "BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj";
    public static final String CONTACT_C = "BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke";
    public String IDENTITY_A;
    public String IDENTITY_B;

    private TestJdbcConfig config;
    private JdbcAddressRepository repo;

    @Before
    public void setUp() throws InterruptedException {
        config = new TestJdbcConfig();
        config.reset();

        repo = new JdbcAddressRepository(config);

        repo.save(new BitmessageAddress(CONTACT_A));
        repo.save(new BitmessageAddress(CONTACT_B));
        repo.save(new BitmessageAddress(CONTACT_C));

        BitmessageAddress identityA = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000));
        repo.save(identityA);
        IDENTITY_A = identityA.getAddress();
        BitmessageAddress identityB = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000));
        repo.save(identityB);
        IDENTITY_B = identityB.getAddress();
    }

    @Test
    public void testFindContact() throws Exception {
        BitmessageAddress address = new BitmessageAddress(CONTACT_A);
        assertEquals(4, address.getVersion());
        assertEquals(address, repo.findContact(address.getTag()));
        assertNull(repo.findIdentity(address.getTag()));
    }

    @Test
    public void testFindIdentity() throws Exception {
        BitmessageAddress identity = new BitmessageAddress(IDENTITY_A);
        assertEquals(4, identity.getVersion());
        assertEquals(identity, repo.findIdentity(identity.getTag()));
        assertNull(repo.findContact(identity.getTag()));
    }

    @Test
    public void testGetIdentities() throws Exception {
        List<BitmessageAddress> identities = repo.getIdentities();
        assertEquals(2, identities.size());
        for (BitmessageAddress identity : identities) {
            assertNotNull(identity.getPrivateKey());
        }
    }

    @Test
    public void testGetSubscriptions() throws Exception {
        addSubscription("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h");
        addSubscription("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        addSubscription("BM-2D9QKN4teYRvoq2fyzpiftPh9WP9qggtzh");
        List<BitmessageAddress> subscriptions = repo.getSubscriptions();
        assertEquals(3, subscriptions.size());
    }

    @Test
    public void testGetSubscriptionsForVersion() throws Exception {
        addSubscription("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h");
        addSubscription("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        addSubscription("BM-2D9QKN4teYRvoq2fyzpiftPh9WP9qggtzh");

        List<BitmessageAddress> subscriptions;

        subscriptions = repo.getSubscriptions(5);
        assertEquals(1, subscriptions.size());

        subscriptions = repo.getSubscriptions(4);
        assertEquals(2, subscriptions.size());
    }

    @Test
    public void testGetContacts() throws Exception {
        List<BitmessageAddress> contacts = repo.getContacts();
        assertEquals(3, contacts.size());
        for (BitmessageAddress contact : contacts) {
            assertNull(contact.getPrivateKey());
        }
    }

    @Test
    public void ensureNewAddressIsSaved() throws Exception {
        repo.save(new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000)));
        List<BitmessageAddress> identities = repo.getIdentities();
        assertEquals(3, identities.size());
    }

    @Test
    public void ensureExistingAddressIsUpdated() throws Exception {
        BitmessageAddress address = repo.getAddress(CONTACT_A);
        address.setAlias("Test-Alias");
        repo.save(address);
        address = repo.getAddress(address.getAddress());
        assertEquals("Test-Alias", address.getAlias());
    }

    @Test
    public void ensureExistingKeysAreNotDeleted() {
        BitmessageAddress address = new BitmessageAddress(IDENTITY_A);
        address.setAlias("Test");
        repo.save(address);
        BitmessageAddress identityA = repo.getAddress(IDENTITY_A);
        assertNotNull(identityA.getPubkey());
        assertNotNull(identityA.getPrivateKey());
        assertEquals("Test", identityA.getAlias());
        assertFalse(identityA.isChan());
    }

    @Test
    public void ensureNewChanIsSavedAndUpdated() {
        BitmessageAddress chan = BitmessageAddress.chan(1, "test");
        repo.save(chan);
        BitmessageAddress address = repo.getAddress(chan.getAddress());
        assertNotNull(address);
        assertTrue(address.isChan());

        address.setAlias("Test");
        repo.save(address);

        address = repo.getAddress(chan.getAddress());
        assertNotNull(address);
        assertTrue(address.isChan());
        assertEquals("Test", address.getAlias());
    }

    @Test
    public void testRemove() throws Exception {
        BitmessageAddress address = repo.getAddress(IDENTITY_A);
        repo.remove(address);
        assertNull(repo.getAddress(IDENTITY_A));
    }

    @Test
    public void testGetAddress() throws Exception {
        BitmessageAddress address = repo.getAddress(IDENTITY_A);
        assertNotNull(address);
        assertNotNull(address.getPrivateKey());
    }

    private void addSubscription(String address) {
        BitmessageAddress subscription = new BitmessageAddress(address);
        subscription.setSubscribed(true);
        repo.save(subscription);
    }
}