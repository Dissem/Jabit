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

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.ports.AddressRepository;
import ch.dissem.bitmessage.ports.MessageRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.Singleton.security;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class JdbcMessageRepositoryTest extends TestBase {
    private BitmessageAddress contactA;
    private BitmessageAddress contactB;
    private BitmessageAddress identity;

    private MessageRepository repo;

    private Label inbox;
    private Label drafts;
    private Label unread;

    @Before
    public void setUp() throws Exception {
        TestJdbcConfig config = new TestJdbcConfig();
        config.reset();
        AddressRepository addressRepo = new JdbcAddressRepository(config);
        repo = new JdbcMessageRepository(config);
        new InternalContext(new BitmessageContext.Builder()
                .cryptography(security())
                .addressRepo(addressRepo)
                .messageRepo(repo)
        );

        BitmessageAddress tmp = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000));
        contactA = new BitmessageAddress(tmp.getAddress());
        contactA.setPubkey(tmp.getPubkey());
        addressRepo.save(contactA);
        contactB = new BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj");
        addressRepo.save(contactB);

        identity = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000));
        addressRepo.save(identity);

        inbox = repo.getLabels(Label.Type.INBOX).get(0);
        drafts = repo.getLabels(Label.Type.DRAFT).get(0);
        unread = repo.getLabels(Label.Type.UNREAD).get(0);

        addMessage(contactA, identity, Plaintext.Status.RECEIVED, inbox, unread);
        addMessage(identity, contactA, Plaintext.Status.DRAFT, drafts);
        addMessage(identity, contactB, Plaintext.Status.DRAFT, unread);
    }

    @Test
    public void ensureLabelsAreRetrieved() throws Exception {
        List<Label> labels = repo.getLabels();
        assertEquals(5, labels.size());
    }

    @Test
    public void ensureLabelsCanBeRetrievedByType() throws Exception {
        List<Label> labels = repo.getLabels(Label.Type.INBOX);
        assertEquals(1, labels.size());
        assertEquals("Inbox", labels.get(0).toString());
    }

    @Test
    public void ensureMessagesCanBeFoundByLabel() throws Exception {
        List<Plaintext> messages = repo.findMessages(inbox);
        assertEquals(1, messages.size());
        Plaintext m = messages.get(0);
        assertEquals(contactA, m.getFrom());
        assertEquals(identity, m.getTo());
        assertEquals(Plaintext.Status.RECEIVED, m.getStatus());
    }

    @Test
    public void ensureUnreadMessagesCanBeFoundForAllLabels() {
        int unread = repo.countUnread(null);
        assertThat(unread, is(2));
    }

    @Test
    public void ensureUnreadMessagesCanBeFoundByLabel() {
        int unread = repo.countUnread(inbox);
        assertThat(unread, is(1));
    }

    @Test
    public void ensureMessageCanBeRetrievedByInitialHash() {
        byte[] initialHash = new byte[64];
        Plaintext message = repo.findMessages(contactA).get(0);
        message.setInitialHash(initialHash);
        repo.save(message);
        Plaintext other = repo.getMessage(initialHash);
        assertThat(other, is(message));
    }

    @Test
    public void testFindMessagesByStatus() throws Exception {
        List<Plaintext> messages = repo.findMessages(Plaintext.Status.RECEIVED);
        assertEquals(1, messages.size());
        Plaintext m = messages.get(0);
        assertEquals(contactA, m.getFrom());
        assertEquals(identity, m.getTo());
        assertEquals(Plaintext.Status.RECEIVED, m.getStatus());
    }

    @Test
    public void testFindMessagesByStatusAndRecipient() throws Exception {
        List<Plaintext> messages = repo.findMessages(Plaintext.Status.DRAFT, contactB);
        assertEquals(1, messages.size());
        Plaintext m = messages.get(0);
        assertEquals(identity, m.getFrom());
        assertEquals(contactB, m.getTo());
        assertEquals(Plaintext.Status.DRAFT, m.getStatus());
    }

    @Test
    public void testSave() throws Exception {
        Plaintext message = new Plaintext.Builder(MSG)
                .IV(new InventoryVector(security().randomBytes(32)))
                .from(identity)
                .to(contactA)
                .message("Subject", "Message")
                .status(Plaintext.Status.DOING_PROOF_OF_WORK)
                .build();
        repo.save(message);

        assertNotNull(message.getId());

        message.addLabels(inbox);
        repo.save(message);

        List<Plaintext> messages = repo.findMessages(Plaintext.Status.DOING_PROOF_OF_WORK);

        assertEquals(1, messages.size());
        assertNotNull(messages.get(0).getInventoryVector());
    }

    @Test
    public void testUpdate() throws Exception {
        List<Plaintext> messages = repo.findMessages(Plaintext.Status.DRAFT, contactA);
        Plaintext message = messages.get(0);
        message.setInventoryVector(new InventoryVector(security().randomBytes(32)));
        repo.save(message);

        messages = repo.findMessages(Plaintext.Status.DRAFT, contactA);
        assertEquals(1, messages.size());
        assertNotNull(messages.get(0).getInventoryVector());
    }

    @Test
    public void testRemove() throws Exception {
        Plaintext toRemove = repo.findMessages(Plaintext.Status.DRAFT, contactB).get(0);
        repo.remove(toRemove);
        List<Plaintext> messages = repo.findMessages(Plaintext.Status.DRAFT);
        assertEquals(1, messages.size());
    }

    private void addMessage(BitmessageAddress from, BitmessageAddress to, Plaintext.Status status, Label... labels) {
        Plaintext message = new Plaintext.Builder(MSG)
                .from(from)
                .to(to)
                .message("Subject", "Message")
                .status(status)
                .labels(Arrays.asList(labels))
                .build();
        repo.save(message);
    }
}