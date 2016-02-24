package ch.dissem.bitmessage.security;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;
import ch.dissem.bitmessage.ports.MultiThreadedPOWEngine;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import ch.dissem.bitmessage.utils.CallbackWaiter;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.UnixTime;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static ch.dissem.bitmessage.utils.UnixTime.DAY;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Christian Basler
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

    private static BouncyCryptography crypto;

    @BeforeClass
    public static void setUp() {
        crypto = new BouncyCryptography();
        Singleton.initialize(crypto);
        InternalContext ctx = mock(InternalContext.class);
        when(ctx.getProofOfWorkEngine()).thenReturn(new MultiThreadedPOWEngine());
        crypto.setContext(ctx);
    }

    @Test
    public void testRipemd160() {
        assertArrayEquals(TEST_RIPEMD160, crypto.ripemd160(TEST_VALUE));
    }

    @Test
    public void testSha1() {
        assertArrayEquals(TEST_SHA1, crypto.sha1(TEST_VALUE));
    }

    @Test
    public void testSha512() {
        assertArrayEquals(TEST_SHA512, crypto.sha512(TEST_VALUE));
    }

    @Test
    public void testChaining() {
        assertArrayEquals(TEST_SHA512, crypto.sha512("test".getBytes(), "string".getBytes()));
    }

    @Test
    public void ensureDoubleHashYieldsSameResultAsHashOfHash() {
        assertArrayEquals(crypto.sha512(TEST_SHA512), crypto.doubleSha512(TEST_VALUE));
    }

    @Test(expected = IOException.class)
    public void ensureExceptionForInsufficientProofOfWork() throws IOException {
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(UnixTime.now(+28 * DAY))
                .objectType(0)
                .payload(GenericPayload.read(0, new ByteArrayInputStream(new byte[0]), 1, 0))
                .build();
        crypto.checkProofOfWork(objectMessage, 1000, 1000);
    }

    @Test
    public void testDoProofOfWork() throws Exception {
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(UnixTime.now(+2 * MINUTE))
                .objectType(0)
                .payload(GenericPayload.read(0, new ByteArrayInputStream(new byte[0]), 1, 0))
                .build();
        final CallbackWaiter<byte[]> waiter = new CallbackWaiter<>();
        crypto.doProofOfWork(objectMessage, 1000, 1000,
                new ProofOfWorkEngine.Callback() {
                    @Override
                    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
                        waiter.setValue(nonce);
                    }
                });
        objectMessage.setNonce(waiter.waitForValue());
        try {
            crypto.checkProofOfWork(objectMessage, 1000, 1000);
        } catch (InsufficientProofOfWorkException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void ensureEncryptionAndDecryptionWorks() {
        byte[] data = crypto.randomBytes(100);
        byte[] key_e = crypto.randomBytes(32);
        byte[] iv = crypto.randomBytes(16);
        byte[] encrypted = crypto.crypt(true, data, key_e, iv);
        byte[] decrypted = crypto.crypt(false, encrypted, key_e, iv);
        assertArrayEquals(data, decrypted);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ensureDecryptionFailsWithInvalidCypherText() {
        byte[] data = crypto.randomBytes(128);
        byte[] key_e = crypto.randomBytes(32);
        byte[] iv = crypto.randomBytes(16);
        crypto.crypt(false, data, key_e, iv);
    }

    @Test
    public void testMultiplication() {
        byte[] a = crypto.randomBytes(PrivateKey.PRIVATE_KEY_SIZE);
        byte[] A = crypto.createPublicKey(a);

        byte[] b = crypto.randomBytes(PrivateKey.PRIVATE_KEY_SIZE);
        byte[] B = crypto.createPublicKey(b);

        assertArrayEquals(crypto.multiply(A, b), crypto.multiply(B, a));
    }

    @Test
    public void ensureSignatureIsValid() {
        byte[] data = crypto.randomBytes(100);
        PrivateKey privateKey = new PrivateKey(false, 1, 1000, 1000);
        byte[] signature = crypto.getSignature(data, privateKey);
        assertThat(crypto.isSignatureValid(data, signature, privateKey.getPubkey()), is(true));
    }

    @Test
    public void ensureSignatureIsInvalidForTemperedData() {
        byte[] data = crypto.randomBytes(100);
        PrivateKey privateKey = new PrivateKey(false, 1, 1000, 1000);
        byte[] signature = crypto.getSignature(data, privateKey);
        data[0]++;
        assertThat(crypto.isSignatureValid(data, signature, privateKey.getPubkey()), is(false));
    }
}
