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
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.entity.valueobject.extended.Message;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.TestUtils;
import ch.dissem.bitmessage.utils.UnixTime;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class JdbcMessageRepositoryTest extends TestBase {
    private BitmessageAddress contactA;
    private BitmessageAddress contactB;
    private BitmessageAddress identity;

    private MessageRepository repo;

    private Label inbox;
    private Label sent;
    private Label drafts;
    private Label unread;

    @Before
    public void setUp() throws Exception {
        TestJdbcConfig config = new TestJdbcConfig();
        config.reset();
        AddressRepository addressRepo = new JdbcAddressRepository(config);
        repo = new JdbcMessageRepository(config);
        new InternalContext(
            cryptography(),
            mock(Inventory.class),
            mock(NodeRegistry.class),
            mock(NetworkHandler.class),
            addressRepo,
            repo,
            mock(ProofOfWorkRepository.class),
            mock(ProofOfWorkEngine.class),
            mock(CustomCommandHandler.class),
            mock(BitmessageContext.Listener.class),
            mock(Labeler.class),
            12345,
            10, 10
        );
        BitmessageAddress tmp = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000, DOES_ACK));
        contactA = new BitmessageAddress(tmp.getAddress());
        contactA.setPubkey(tmp.getPubkey());
        addressRepo.save(contactA);
        contactB = new BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj");
        addressRepo.save(contactB);

        identity = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000, DOES_ACK));
        addressRepo.save(identity);

        inbox = repo.getLabels(Label.Type.INBOX).get(0);
        sent = repo.getLabels(Label.Type.SENT).get(0);
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
    public void ensureAckMessageCanBeUpdatedAndRetrieved() {
        byte[] initialHash = new byte[64];
        Plaintext message = repo.findMessages(contactA).get(0);
        message.setInitialHash(initialHash);
        ObjectMessage ackMessage = message.getAckMessage();
        repo.save(message);
        Plaintext other = repo.getMessage(initialHash);
        assertThat(other, is(message));
        assertThat(other.getAckMessage(), is(ackMessage));
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
            .IV(TestUtils.randomInventoryVector())
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
        message.setInventoryVector(TestUtils.randomInventoryVector());
        repo.save(message);

        messages = repo.findMessages(Plaintext.Status.DRAFT, contactA);
        assertEquals(1, messages.size());
        assertNotNull(messages.get(0).getInventoryVector());
    }

    @Test
    public void ensureMessageIsRemoved() throws Exception {
        Plaintext toRemove = repo.findMessages(Plaintext.Status.DRAFT, contactB).get(0);
        List<Plaintext> messages = repo.findMessages(Plaintext.Status.DRAFT);
        assertEquals(2, messages.size());
        repo.remove(toRemove);
        messages = repo.findMessages(Plaintext.Status.DRAFT);
        assertThat(messages, hasSize(1));
    }

    @Test
    public void ensureUnacknowledgedMessagesAreFoundForResend() throws Exception {
        Plaintext message = new Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(identity)
            .to(contactA)
            .message("Subject", "Message")
            .sent(UnixTime.now())
            .status(Plaintext.Status.SENT)
            .ttl(2)
            .build();
        message.updateNextTry();
        assertThat(message.getRetries(), is(1));
        assertThat(message.getNextTry(), greaterThan(UnixTime.now()));
        assertThat(message.getNextTry(), lessThanOrEqualTo(UnixTime.now() + 2));
        repo.save(message);
        Thread.sleep(4100); // somewhat longer than 2*TTL
        List<Plaintext> messagesToResend = repo.findMessagesToResend();
        assertThat(messagesToResend, hasSize(1));

        message.updateNextTry();
        assertThat(message.getRetries(), is(2));
        assertThat(message.getNextTry(), greaterThan(UnixTime.now()));
        repo.save(message);
        messagesToResend = repo.findMessagesToResend();
        assertThat(messagesToResend, empty());
    }

    @Test
    public void ensureParentsAreSaved() {
        Plaintext parent = storeConversation();

        List<Plaintext> responses = repo.findResponses(parent);
        assertThat(responses, hasSize(2));
        assertThat(responses, hasItem(hasMessage("Re: new test", "Nice!")));
        assertThat(responses, hasItem(hasMessage("Re: new test", "PS: it did work!")));
    }

    @Test
    public void ensureConversationCanBeRetrieved() {
        Plaintext root = storeConversation();
        List<UUID> conversations = repo.findConversations(inbox);
        assertThat(conversations, hasSize(2));
        assertThat(conversations, hasItem(root.getConversationId()));
    }

    private Plaintext addMessage(BitmessageAddress from, BitmessageAddress to, Plaintext.Status status, Label... labels) {
        ExtendedEncoding content = new Message.Builder()
            .subject("Subject")
            .body("Message")
            .build();
        return addMessage(from, to, content, status, labels);
    }

    private Plaintext addMessage(BitmessageAddress from, BitmessageAddress to,
                                 ExtendedEncoding content, Plaintext.Status status, Label... labels) {
        Plaintext message = new Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(from)
            .to(to)
            .message(content)
            .status(status)
            .labels(Arrays.asList(labels))
            .build();
        repo.save(message);
        return message;
    }

    private Plaintext storeConversation() {
        Plaintext older = addMessage(identity, contactA,
            new Message.Builder()
                .subject("hey there")
                .body("does it work?")
                .build(),
            Plaintext.Status.SENT, sent);

        Plaintext root = addMessage(identity, contactA,
            new Message.Builder()
                .subject("new test")
                .body("There's a new test in town!")
                .build(),
            Plaintext.Status.SENT, sent);

        addMessage(contactA, identity,
            new Message.Builder()
                .subject("Re: new test")
                .body("Nice!")
                .addParent(root)
                .build(),
            Plaintext.Status.RECEIVED, inbox);

        addMessage(contactA, identity,
            new Message.Builder()
                .subject("Re: new test")
                .body("PS: it did work!")
                .addParent(root)
                .addParent(older)
                .build(),
            Plaintext.Status.RECEIVED, inbox);

        return repo.getMessage(root.getId());
    }

    private Matcher<Plaintext> hasMessage(String subject, String body) {
        return new BaseMatcher<Plaintext>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Subject: ").appendText(subject);
                description.appendText(", ");
                description.appendText("Body: ").appendText(body);
            }

            @Override
            public boolean matches(Object item) {
                if (item instanceof Plaintext) {
                    Plaintext message = (Plaintext) item;
                    if (subject != null && !subject.equals(message.getSubject())) {
                        return false;
                    }
                    if (body != null && !body.equals(message.getText())) {
                        return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }
        };
    }
}
