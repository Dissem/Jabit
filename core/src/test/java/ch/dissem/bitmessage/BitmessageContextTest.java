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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.MessageMatchers;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.TestUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static ch.dissem.bitmessage.entity.payload.ObjectType.*;
import static ch.dissem.bitmessage.utils.MessageMatchers.object;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Christian Basler
 */
public class BitmessageContextTest {
    private BitmessageContext ctx;
    private BitmessageContext.Listener listener;

    @Before
    public void setUp() throws Exception {
        Singleton.initialize(null);
        listener = mock(BitmessageContext.Listener.class);
        ctx = new BitmessageContext.Builder()
                .addressRepo(mock(AddressRepository.class))
                .cryptography(new BouncyCryptography())
                .inventory(mock(Inventory.class))
                .listener(listener)
                .messageCallback(mock(MessageCallback.class))
                .messageRepo(mock(MessageRepository.class))
                .networkHandler(mock(NetworkHandler.class))
                .nodeRegistry(mock(NodeRegistry.class))
                .powRepo(mock(ProofOfWorkRepository.class))
                .proofOfWorkEngine(mock(ProofOfWorkEngine.class))
                .build();
    }

    @Test
    public void ensureContactIsSavedAndPubkeyRequested() {
        BitmessageAddress contact = new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT");
        ctx.addContact(contact);

        verify(ctx.addresses(), times(2)).save(contact);
        verify(ctx.internals().getProofOfWorkEngine())
                .calculateNonce(any(byte[].class), any(byte[].class), any(ProofOfWorkEngine.Callback.class));
    }

    @Test
    public void ensurePubkeyIsNotRequestedIfItExists() throws Exception {
        ObjectMessage object = TestUtils.loadObjectMessage(2, "V2Pubkey.payload");
        Pubkey pubkey = (Pubkey) object.getPayload();
        BitmessageAddress contact = new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT");
        contact.setPubkey(pubkey);

        ctx.addContact(contact);

        verify(ctx.addresses(), times(1)).save(contact);
        verify(ctx.internals().getProofOfWorkEngine(), never())
                .calculateNonce(any(byte[].class), any(byte[].class), any(ProofOfWorkEngine.Callback.class));
    }

    @Test
    public void ensureV2PubkeyIsNotRequestedIfItExistsInInventory() throws Exception {
        BitmessageAddress contact = new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT");
        when(ctx.internals().getInventory().getObjects(anyLong(), anyLong(), any(ObjectType.class)))
                .thenReturn(Collections.singletonList(
                        TestUtils.loadObjectMessage(2, "V2Pubkey.payload")
                ));

        ctx.addContact(contact);

        verify(ctx.addresses(), atLeastOnce()).save(contact);
        verify(ctx.internals().getProofOfWorkEngine(), never())
                .calculateNonce(any(byte[].class), any(byte[].class), any(ProofOfWorkEngine.Callback.class));
    }

    @Test
    public void ensureV4PubkeyIsNotRequestedIfItExistsInInventory() throws Exception {
        BitmessageAddress contact = new BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h");
        when(ctx.internals().getInventory().getObjects(anyLong(), anyLong(), any(ObjectType.class)))
                .thenReturn(Collections.singletonList(
                        TestUtils.loadObjectMessage(2, "V4Pubkey.payload")
                ));
        final BitmessageAddress stored = new BitmessageAddress(contact.getAddress());
        stored.setAlias("Test");
        when(ctx.addresses().getAddress(contact.getAddress())).thenReturn(stored);

        ctx.addContact(contact);

        verify(ctx.addresses(), atLeastOnce()).save(argThat(new BaseMatcher<BitmessageAddress>() {
            @Override
            public boolean matches(Object item) {
                return item instanceof BitmessageAddress
                        && ((BitmessageAddress) item).getPubkey() != null
                        && stored.getAlias().equals(((BitmessageAddress) item).getAlias());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("pubkey must not be null and alias must be ").appendValue(stored.getAlias());
            }
        }));
        verify(ctx.internals().getProofOfWorkEngine(), never())
                .calculateNonce(any(byte[].class), any(byte[].class), any(ProofOfWorkEngine.Callback.class));
    }

    @Test
    public void ensureSubscriptionIsAddedAndExistingBroadcastsRetrieved() throws Exception {
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");

        List<ObjectMessage> objects = new LinkedList<>();
        objects.add(TestUtils.loadObjectMessage(4, "V4Broadcast.payload"));
        objects.add(TestUtils.loadObjectMessage(5, "V5Broadcast.payload"));
        when(ctx.internals().getInventory().getObjects(eq(address.getStream()), anyLong(), any(ObjectType.class)))
                .thenReturn(objects);
        when(ctx.addresses().getSubscriptions(anyLong())).thenReturn(Collections.singletonList(address));

        ctx.addSubscribtion(address);

        verify(ctx.addresses(), atLeastOnce()).save(address);
        assertThat(address.isSubscribed(), is(true));
        verify(ctx.internals().getInventory()).getObjects(eq(address.getStream()), anyLong(), any(ObjectType.class));
        verify(listener).receive(any(Plaintext.class));
    }

    @Test
    public void ensureIdentityIsCreated() {
        assertThat(ctx.createIdentity(false), notNullValue());
    }

    @Test
    public void ensureMessageIsSent() throws Exception {
        ctx.send(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"), TestUtils.loadContact(),
                "Subject", "Message");
        verify(ctx.internals().getProofOfWorkRepository(), timeout(10000).atLeastOnce())
                .putObject(object(MSG), eq(1000L), eq(1000L));
        verify(ctx.messages(), timeout(10000).atLeastOnce()).save(MessageMatchers.plaintext(Plaintext.Type.MSG));
    }

    @Test
    public void ensurePubkeyIsRequestedIfItIsMissing() throws Exception {
        ctx.send(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"),
                new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
                "Subject", "Message");
        verify(ctx.internals().getProofOfWorkRepository(), timeout(10000).atLeastOnce())
                .putObject(object(GET_PUBKEY), eq(1000L), eq(1000L));
        verify(ctx.messages(), timeout(10000).atLeastOnce()).save(MessageMatchers.plaintext(Plaintext.Type.MSG));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureSenderMustBeIdentity() {
        ctx.send(new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
                new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT"),
                "Subject", "Message");
    }

    @Test
    public void ensureBroadcastIsSent() throws Exception {
        ctx.broadcast(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"),
                "Subject", "Message");
        verify(ctx.internals().getProofOfWorkRepository(), timeout(10000).atLeastOnce())
                .putObject(object(BROADCAST), eq(1000L), eq(1000L));
        verify(ctx.internals().getProofOfWorkEngine())
                .calculateNonce(any(byte[].class), any(byte[].class), any(ProofOfWorkEngine.Callback.class));
        verify(ctx.messages(), timeout(10000).atLeastOnce())
                .save(MessageMatchers.plaintext(Plaintext.Type.BROADCAST));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureSenderWithoutPrivateKeyThrowsException() {
        Plaintext msg = new Plaintext.Builder(Plaintext.Type.BROADCAST)
                .from(new BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
                .message("Subject", "Message")
                .build();
        ctx.send(msg);
    }

    @Test
    public void ensureChanIsJoined() {
        String chanAddress = "BM-2cW67GEKkHGonXKZLCzouLLxnLym3azS8r";
        BitmessageAddress chan = ctx.joinChan("general", chanAddress);
        assertNotNull(chan);
        assertEquals(chan.getAddress(), chanAddress);
        assertTrue(chan.isChan());
    }

    @Test
    public void ensureDeterministicAddressesAreCreated() {
        final int expected_size = 8;
        List<BitmessageAddress> addresses = ctx.createDeterministicAddresses("test", expected_size, 4, 1, false);
        assertEquals(expected_size, addresses.size());
        Set<String> expected = new HashSet<>(expected_size);
        expected.add("BM-2cWFkyuXXFw6d393RGnin2RpSXj8wxtt6F");
        expected.add("BM-2cX8TF9vuQZEWvT7UrEeq1HN9dgiSUPLEN");
        expected.add("BM-2cUzX8f9CKUU7L8NeB8GExZvf54PrcXq1S");
        expected.add("BM-2cU7MAoQd7KE8SPF7AKFPpoEZKjk86KRqE");
        expected.add("BM-2cVm8ByVBacc2DVhdTNs6rmy5ZQK6DUsrt");
        expected.add("BM-2cW2af1vB6kWon2WkygDHqGwfcpfAFm2Jk");
        expected.add("BM-2cWdWD7UtUN4gWChgNX9pvyvNPjUZvU8BT");
        expected.add("BM-2cXkYgYcUrv4fGxSHzyEScW955Cc8sDteo");
        for (BitmessageAddress a : addresses) {
            assertTrue(expected.contains(a.getAddress()));
            expected.remove(a.getAddress());
        }
    }

    @Test
    public void ensureShortDeterministicAddressesAreCreated() {
        final int expected_size = 1;
        List<BitmessageAddress> addresses = ctx.createDeterministicAddresses("test", expected_size, 4, 1, true);
        assertEquals(expected_size, addresses.size());
        Set<String> expected = new HashSet<>(expected_size);
        expected.add("BM-NBGyBAEp6VnBkFWKpzUSgxuTqVdWPi78");
        for (BitmessageAddress a : addresses) {
            assertTrue(expected.contains(a.getAddress()));
            expected.remove(a.getAddress());
        }
    }

    @Test
    public void ensureChanIsCreated() {
        BitmessageAddress chan = ctx.createChan("test");
        assertNotNull(chan);
        assertEquals(chan.getVersion(), Pubkey.LATEST_VERSION);
        assertTrue(chan.isChan());
    }
}
