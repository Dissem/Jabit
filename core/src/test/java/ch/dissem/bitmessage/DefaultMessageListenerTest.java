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
import ch.dissem.bitmessage.entity.payload.Broadcast;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.TestBase;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static ch.dissem.bitmessage.entity.Plaintext.Status.PUBKEY_REQUESTED;
import static ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST;
import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.MessageMatchers.plaintext;
import static org.mockito.Mockito.*;

/**
 * @author Christian Basler
 */
public class DefaultMessageListenerTest extends TestBase {
    @Mock
    private AddressRepository addressRepo;
    @Mock
    private MessageRepository messageRepo;
    @Mock
    private Inventory inventory;
    @Mock
    private NetworkHandler networkHandler;

    private InternalContext ctx;
    private DefaultMessageListener listener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ctx = mock(InternalContext.class);
        Singleton.initialize(new BouncyCryptography());
        when(ctx.getAddressRepository()).thenReturn(addressRepo);
        when(ctx.getMessageRepository()).thenReturn(messageRepo);
        when(ctx.getInventory()).thenReturn(inventory);
        when(ctx.getNetworkHandler()).thenReturn(networkHandler);
        when(ctx.getLabeler()).thenReturn(mock(Labeler.class));

        listener = new DefaultMessageListener(ctx, mock(Labeler.class), mock(BitmessageContext.Listener.class));
    }

    @Test
    public void ensurePubkeyIsSentOnRequest() throws Exception {
        BitmessageAddress identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        when(addressRepo.findIdentity(any(byte[].class)))
                .thenReturn(identity);
        listener.receive(new ObjectMessage.Builder()
                .stream(2)
                .payload(new GetPubkey(new BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")))
                .build());
        verify(ctx).sendPubkey(eq(identity), eq(2L));
    }

    @Test
    public void ensureIncomingPubkeyIsAddedToContact() throws Exception {
        BitmessageAddress identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        BitmessageAddress contact = new BitmessageAddress(identity.getAddress());
        when(addressRepo.findContact(any(byte[].class)))
                .thenReturn(contact);
        when(messageRepo.findMessages(eq(PUBKEY_REQUESTED), eq(contact)))
                .thenReturn(Collections.singletonList(
                        new Plaintext.Builder(MSG).from(identity).to(contact).message("S", "T").build()
                ));

        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .stream(2)
                .payload(identity.getPubkey())
                .build();
        objectMessage.sign(identity.getPrivateKey());
        objectMessage.encrypt(Singleton.security().createPublicKey(identity.getPublicDecryptionKey()));
        listener.receive(objectMessage);

        verify(addressRepo).save(any(BitmessageAddress.class));
    }

    @Test
    public void ensureIncomingMessageIsSaved() throws Exception {
        BitmessageAddress identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        BitmessageAddress contact = new BitmessageAddress(identity.getAddress());
        contact.setPubkey(identity.getPubkey());

        when(addressRepo.getIdentities()).thenReturn(Collections.singletonList(identity));

        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .stream(2)
                .payload(new Msg(new Plaintext.Builder(MSG)
                        .from(identity)
                        .to(contact)
                        .message("S", "T")
                        .build()))
                .nonce(new byte[8])
                .build();
        objectMessage.sign(identity.getPrivateKey());
        objectMessage.encrypt(identity.getPubkey());

        listener.receive(objectMessage);

        verify(messageRepo, atLeastOnce()).save(plaintext(MSG));
    }

    @Test
    public void ensureIncomingBroadcastIsSaved() throws Exception {
        BitmessageAddress identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");

        when(addressRepo.getSubscriptions(anyLong())).thenReturn(Collections.singletonList(identity));

        Broadcast broadcast = Factory.getBroadcast(new Plaintext.Builder(BROADCAST)
                .from(identity)
                .message("S", "T")
                .build());
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .stream(2)
                .payload(broadcast)
                .nonce(new byte[8])
                .build();
        objectMessage.sign(identity.getPrivateKey());
        broadcast.encrypt();

        listener.receive(objectMessage);

        verify(messageRepo, atLeastOnce()).save(plaintext(BROADCAST));
    }
}
