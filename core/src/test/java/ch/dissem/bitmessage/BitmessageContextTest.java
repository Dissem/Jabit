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
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.entity.payload.ObjectType.*;
import static ch.dissem.bitmessage.utils.MessageMatchers.object;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Christian Basler
 */
public class BitmessageContextTest {
    private BitmessageContext ctx;
    private BitmessageContext.Listener listener;

    @Before
    public void setUp() throws Exception {
        Field field = Singleton.class.getDeclaredField("cryptography");
        field.setAccessible(true);
        field.set(null, null);

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
    public void ensureSubscriptionIsAddedAndExistingBroadcastsRetrieved() throws Exception {
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");

        List<ObjectMessage> objects = new LinkedList<>();
        objects.add(TestUtils.loadObjectMessage(4, "V4Broadcast.payload"));
        objects.add(TestUtils.loadObjectMessage(5, "V5Broadcast.payload"));
        when(ctx.internals().getInventory().getObjects(eq(address.getStream()), anyLong(), any(ObjectType.class)))
                .thenReturn(objects);

        ctx.addSubscribtion(address);

        verify(ctx.addresses(), times(1)).save(address);
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
        verify(ctx.messages(), timeout(10000).atLeastOnce()).save(MessageMatchers.plaintext(Plaintext.Type.BROADCAST));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureSenderWithoutPrivateKeyThrowsException() {
        Plaintext msg = new Plaintext.Builder(Plaintext.Type.BROADCAST)
                .from(new BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
                .message("Subject", "Message")
                .build();
        ctx.send(msg);
    }
}
