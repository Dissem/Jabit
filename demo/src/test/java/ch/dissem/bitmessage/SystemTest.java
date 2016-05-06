package ch.dissem.bitmessage;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.networking.DefaultNetworkHandler;
import ch.dissem.bitmessage.ports.DefaultLabeler;
import ch.dissem.bitmessage.ports.Labeler;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.utils.TTL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;

/**
 * @author Christian Basler
 */
public class SystemTest {
    private static int port = 6000;

    private BitmessageContext alice;
    private TestListener aliceListener = new TestListener();
    private Labeler aliceLabeler = Mockito.spy(new DebugLabeler("Alice"));
    private BitmessageAddress aliceIdentity;

    private BitmessageContext bob;
    private TestListener bobListener = new TestListener();
    private BitmessageAddress bobIdentity;

    @Before
    public void setUp() {
        int alicePort = port++;
        int bobPort = port++;
        TTL.msg(5 * MINUTE);
        TTL.getpubkey(5 * MINUTE);
        TTL.pubkey(5 * MINUTE);
        JdbcConfig aliceDB = new JdbcConfig("jdbc:h2:mem:alice;DB_CLOSE_DELAY=-1", "sa", "");
        alice = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(aliceDB))
                .inventory(new JdbcInventory(aliceDB))
                .messageRepo(new JdbcMessageRepository(aliceDB))
                .powRepo(new JdbcProofOfWorkRepository(aliceDB))
                .port(alicePort)
                .nodeRegistry(new TestNodeRegistry(bobPort))
                .networkHandler(new DefaultNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(aliceListener)
                .labeler(aliceLabeler)
                .build();
        alice.startup();
        aliceIdentity = alice.createIdentity(false, DOES_ACK);

        JdbcConfig bobDB = new JdbcConfig("jdbc:h2:mem:bob;DB_CLOSE_DELAY=-1", "sa", "");
        bob = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(bobDB))
                .inventory(new JdbcInventory(bobDB))
                .messageRepo(new JdbcMessageRepository(bobDB))
                .powRepo(new JdbcProofOfWorkRepository(bobDB))
                .port(bobPort)
                .nodeRegistry(new TestNodeRegistry(alicePort))
                .networkHandler(new DefaultNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(bobListener)
                .labeler(new DebugLabeler("Bob"))
                .build();
        bob.startup();
        bobIdentity = bob.createIdentity(false, DOES_ACK);

        ((DebugLabeler) alice.labeler()).init(aliceIdentity, bobIdentity);
        ((DebugLabeler) bob.labeler()).init(aliceIdentity, bobIdentity);
    }

    @After
    public void tearDown() {
        alice.shutdown();
        bob.shutdown();
    }

    @Test
    public void ensureAliceCanSendMessageToBob() throws Exception {
        String originalMessage = UUID.randomUUID().toString();
        alice.send(aliceIdentity, new BitmessageAddress(bobIdentity.getAddress()), "Subject", originalMessage);

        Plaintext plaintext = bobListener.get(15, TimeUnit.MINUTES);

        assertThat(plaintext.getType(), equalTo(Plaintext.Type.MSG));
        assertThat(plaintext.getText(), equalTo(originalMessage));

        Mockito.verify(aliceLabeler, Mockito.timeout(TimeUnit.MINUTES.toMillis(15)).atLeastOnce())
                .markAsAcknowledged(any());
    }

    @Test
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
