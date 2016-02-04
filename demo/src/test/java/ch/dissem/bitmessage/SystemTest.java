package ch.dissem.bitmessage;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.networking.DefaultNetworkHandler;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.utils.TTL;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Basler
 */
public class SystemTest {
    static BitmessageContext alice;
    static TestListener aliceListener = new TestListener();
    static BitmessageAddress aliceIdentity;

    static BitmessageContext bob;
    static TestListener bobListener = new TestListener();
    static BitmessageAddress bobIdentity;

    @BeforeClass
    public static void setUp() {
        TTL.msg(5 * MINUTE);
        TTL.getpubkey(5 * MINUTE);
        TTL.pubkey(5 * MINUTE);
        JdbcConfig aliceDB = new JdbcConfig("jdbc:h2:mem:alice;DB_CLOSE_DELAY=-1", "sa", "");
        alice = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(aliceDB))
                .inventory(new JdbcInventory(aliceDB))
                .messageRepo(new JdbcMessageRepository(aliceDB))
                .powRepo(new JdbcProofOfWorkRepository(aliceDB))
                .port(6001)
                .nodeRegistry(new TestNodeRegistry(6002))
                .networkHandler(new DefaultNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(aliceListener)
                .build();
        alice.startup();
        aliceIdentity = alice.createIdentity(false);

        JdbcConfig bobDB = new JdbcConfig("jdbc:h2:mem:bob;DB_CLOSE_DELAY=-1", "sa", "");
        bob = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(bobDB))
                .inventory(new JdbcInventory(bobDB))
                .messageRepo(new JdbcMessageRepository(bobDB))
                .powRepo(new JdbcProofOfWorkRepository(bobDB))
                .port(6002)
                .nodeRegistry(new TestNodeRegistry(6001))
                .networkHandler(new DefaultNetworkHandler())
                .cryptography(new BouncyCryptography())
                .listener(bobListener)
                .build();
        bob.startup();
        bobIdentity = bob.createIdentity(false);
    }

    @AfterClass
    public static void tearDown() {
        alice.shutdown();
        bob.shutdown();
    }

    @Test
    public void ensureAliceCanSendMessageToBob() throws Exception {
        bobListener.reset();
        String originalMessage = UUID.randomUUID().toString();
        alice.send(aliceIdentity, new BitmessageAddress(bobIdentity.getAddress()), "Subject", originalMessage);

        Plaintext plaintext = bobListener.get(15, TimeUnit.MINUTES);

        assertThat(plaintext.getText(), equalTo(originalMessage));
    }
}
