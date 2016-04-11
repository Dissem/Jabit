package ch.dissem.bitmessage;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.networking.DefaultNetworkHandler;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.utils.TTL;
import org.junit.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author Christian Basler
 */
public class SystemTest {
    private static int port = 6000;

    private BitmessageContext alice;
    private TestListener aliceListener = new TestListener();
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
                .build();
        alice.startup();
        aliceIdentity = alice.createIdentity(false);

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
                .build();
        bob.startup();
        bobIdentity = bob.createIdentity(false);
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
}
