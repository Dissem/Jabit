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
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.networking.nio.NioNetworkHandler;
import ch.dissem.bitmessage.ports.DefaultLabeler;
import ch.dissem.bitmessage.ports.Labeler;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.utils.TTL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static com.nhaarman.mockito_kotlin.MockitoKt.spy;
import static com.nhaarman.mockito_kotlin.MockitoKt.timeout;
import static com.nhaarman.mockito_kotlin.MockitoKt.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * @author Christian Basler
 */
public class SystemTest {
    private static int port = 6000;

    private BitmessageContext alice;
    private BitmessageAddress aliceIdentity;
    private Labeler aliceLabeler;

    private BitmessageContext bob;
    private TestListener bobListener;
    private BitmessageAddress bobIdentity;

    @Before
    public void setUp() {
        TTL.msg(5 * MINUTE);
        TTL.getpubkey(5 * MINUTE);
        TTL.pubkey(5 * MINUTE);

        int alicePort = port++;
        int bobPort = port++;
        {
            JdbcConfig aliceDB = new JdbcConfig("jdbc:h2:mem:alice;DB_CLOSE_DELAY=-1", "sa", "");
            aliceLabeler = spy(new DebugLabeler("Alice"));
            TestListener aliceListener = new TestListener();
            alice = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(aliceDB))
                .inventory(new JdbcInventory(aliceDB))
                .messageRepo(new JdbcMessageRepository(aliceDB))
                .powRepo(new JdbcProofOfWorkRepository(aliceDB))
                .nodeRegistry(new TestNodeRegistry(bobPort))
                .networkHandler(new NioNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(aliceListener)
                .labeler(aliceLabeler)
                .build();
            alice.internals().getPreferences().setPort(alicePort);
            alice.startup();
            aliceIdentity = alice.createIdentity(false, DOES_ACK);
        }
        {
            JdbcConfig bobDB = new JdbcConfig("jdbc:h2:mem:bob;DB_CLOSE_DELAY=-1", "sa", "");
            bobListener = new TestListener();
            bob = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(bobDB))
                .inventory(new JdbcInventory(bobDB))
                .messageRepo(new JdbcMessageRepository(bobDB))
                .powRepo(new JdbcProofOfWorkRepository(bobDB))
                .nodeRegistry(new TestNodeRegistry(alicePort))
                .networkHandler(new NioNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(bobListener)
                .labeler(new DebugLabeler("Bob"))
                .build();
            bob.internals().getPreferences().setPort(bobPort);
            bob.startup();
            bobIdentity = bob.createIdentity(false, DOES_ACK);
        }
        ((DebugLabeler) alice.labeler()).init(aliceIdentity, bobIdentity);
        ((DebugLabeler) bob.labeler()).init(aliceIdentity, bobIdentity);
    }

    @After
    public void tearDown() {
        alice.shutdown();
        bob.shutdown();
    }

    @Test(timeout = 120_000)
    public void ensureAliceCanSendMessageToBob() throws Exception {
        String originalMessage = UUID.randomUUID().toString();
        alice.send(aliceIdentity, new BitmessageAddress(bobIdentity.getAddress()), "Subject", originalMessage);

        Plaintext plaintext = bobListener.get(2, TimeUnit.MINUTES);

        assertThat(plaintext.getType(), equalTo(Plaintext.Type.MSG));
        assertThat(plaintext.getText(), equalTo(originalMessage));

        verify(aliceLabeler, timeout(TimeUnit.MINUTES.toMillis(2)).atLeastOnce())
            .markAsAcknowledged(any());
    }

    @Test(timeout = 30_000)
    public void ensureBobCanReceiveBroadcastFromAlice() throws Exception {
        String originalMessage = UUID.randomUUID().toString();
        bob.addSubscribtion(new BitmessageAddress(aliceIdentity.getAddress()));
        alice.broadcast(aliceIdentity, "Subject", originalMessage);

        Plaintext plaintext = bobListener.get(15, TimeUnit.MINUTES);

        assertThat(plaintext.getType(), equalTo(Plaintext.Type.BROADCAST));
        assertThat(plaintext.getText(), equalTo(originalMessage));
    }

    private static class DebugLabeler extends DefaultLabeler {
        private final Logger LOG = LoggerFactory.getLogger("Labeler");
        final String name;
        String alice;
        String bob;

        private DebugLabeler(String name) {
            this.name = name;
        }

        private void init(BitmessageAddress alice, BitmessageAddress bob) {
            this.alice = alice.getAddress();
            this.bob = bob.getAddress();
        }

        @Override
        public void setLabels(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Received");
            super.setLabels(msg);
        }

        @Override
        public void markAsDraft(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Draft");
            super.markAsDraft(msg);
        }

        @Override
        public void markAsSending(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Sending");
            super.markAsSending(msg);
        }

        @Override
        public void markAsSent(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Sent");
            super.markAsSent(msg);
        }

        @Override
        public void markAsAcknowledged(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Acknowledged");
            super.markAsAcknowledged(msg);
        }

        @Override
        public void markAsRead(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Read");
            super.markAsRead(msg);
        }

        @Override
        public void markAsUnread(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Unread");
            super.markAsUnread(msg);
        }

        @Override
        public void delete(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Cleared");
            super.delete(msg);
        }

        @Override
        public void archive(Plaintext msg) {
            LOG.info(name + ": From " + name(msg.getFrom()) + ": Archived");
            super.archive(msg);
        }

        private String name(BitmessageAddress address) {
            if (alice.equals(address.getAddress()))
                return "Alice";
            else if (bob.equals(address.getAddress()))
                return "Bob";
            else
                return "Unknown (" + address.getAddress() + ")";
        }
    }
}
