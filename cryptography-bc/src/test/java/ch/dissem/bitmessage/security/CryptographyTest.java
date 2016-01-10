package ch.dissem.bitmessage.security;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.ports.MultiThreadedPOWEngine;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.utils.CallbackWaiter;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.UnixTime;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static ch.dissem.bitmessage.utils.UnixTime.DAY;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by chris on 19.07.15.
 */
public class CryptographyTest {
    public static final byte[] TEST_VALUE = "teststring".getBytes();
    public static final byte[] TEST_SHA1 = DatatypeConverter.parseHexBinary(""
            + "b8473b86d4c2072ca9b08bd28e373e8253e865c4");
    public static final byte[] TEST_SHA512 = DatatypeConverter.parseHexBinary(""
            + "6253b39071e5df8b5098f59202d414c37a17d6a38a875ef5f8c7d89b0212b028"
            + "692d3d2090ce03ae1de66c862fa8a561e57ed9eb7935ce627344f742c0931d72");
    public static final byte[] TEST_RIPEMD160 = DatatypeConverter.parseHexBinary(""
            + "cd566972b5e50104011a92b59fa8e0b1234851ae");

    private static BouncyCryptography security;

    public CryptographyTest() {
        security = new BouncyCryptography();
        Singleton.initialize(security);
        InternalContext ctx = mock(InternalContext.class);
        when(ctx.getProofOfWorkEngine()).thenReturn(new MultiThreadedPOWEngine());
        security.setContext(ctx);
    }

    @Test
    public void testRipemd160() {
        assertArrayEquals(TEST_RIPEMD160, security.ripemd160(TEST_VALUE));
    }

    @Test
    public void testSha1() {
        assertArrayEquals(TEST_SHA1, security.sha1(TEST_VALUE));
    }

    @Test
    public void testSha512() {
        assertArrayEquals(TEST_SHA512, security.sha512(TEST_VALUE));
    }

    @Test
    public void testChaining() {
        assertArrayEquals(TEST_SHA512, security.sha512("test".getBytes(), "string".getBytes()));
    }

    @Test
    public void testDoubleHash() {
        assertArrayEquals(security.sha512(TEST_SHA512), security.doubleSha512(TEST_VALUE));
    }

    @Test(expected = IOException.class)
    public void testProofOfWorkFails() throws IOException {
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(UnixTime.now(+2 * DAY)) // 5 minutes
                .objectType(0)
                .payload(GenericPayload.read(0, new ByteArrayInputStream(new byte[0]), 1, 0))
                .build();
        security.checkProofOfWork(objectMessage, 1000, 1000);
    }

    @Test
    public void testDoProofOfWork() throws Exception {
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(UnixTime.now(+2 * DAY))
                .objectType(0)
                .payload(GenericPayload.read(0, new ByteArrayInputStream(new byte[0]), 1, 0))
                .build();
        final CallbackWaiter<byte[]> waiter = new CallbackWaiter<>();
        security.doProofOfWork(objectMessage, 1000, 1000,
                new ProofOfWorkEngine.Callback() {
                    @Override
                    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
                        waiter.setValue(nonce);
                    }
                });
        objectMessage.setNonce(waiter.waitForValue());
        security.checkProofOfWork(objectMessage, 1000, 1000);
    }
}