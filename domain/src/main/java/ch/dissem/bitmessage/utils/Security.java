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

package ch.dissem.bitmessage.utils;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * Provides some methods to help with hashing and encryption. All randoms are created using {@link SecureRandom},
 * which should be secure enough.
 */
public class Security {
    public static final Logger LOG = LoggerFactory.getLogger(Security.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final X9ECParameters EC_CURVE_PARAMETERS = CustomNamedCurves.getByName("secp256k1");

    static {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * A helper method to calculate SHA-512 hashes. Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.
     *
     * @param data to get hashed
     * @return SHA-512 hash of data
     */
    public static byte[] sha512(byte[]... data) {
        return hash("SHA-512", data);
    }

    /**
     * A helper method to calculate doubleSHA-512 hashes. Please note that a new {@link MessageDigest} object is created
     * at each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.
     *
     * @param data to get hashed
     * @return SHA-512 hash of data
     */
    public static byte[] doubleSha512(byte[]... data) {
        MessageDigest mda = md("SHA-512");
        for (byte[] d : data) {
            mda.update(d);
        }
        return mda.digest(mda.digest());
    }

    /**
     * A helper method to calculate double SHA-512 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at each call (to ensure thread safety), so you
     * shouldn't use this if you need to do many hash calculations in short order on the same thread.
     * </p>
     *
     * @param data   to get hashed
     * @param length number of bytes to be taken into account
     * @return SHA-512 hash of data
     */
    public static byte[] doubleSha512(byte[] data, int length) {
        MessageDigest mda = md("SHA-512");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }


    /**
     * A helper method to calculate RIPEMD-160 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data to get hashed
     * @return RIPEMD-160 hash of data
     */
    public static byte[] ripemd160(byte[]... data) {
        return hash("RIPEMD160", data);
    }

    /**
     * A helper method to calculate double SHA-256 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data   to get hashed
     * @param length number of bytes to be taken into account
     * @return SHA-256 hash of data
     */
    public static byte[] doubleSha256(byte[] data, int length) {
        MessageDigest mda = md("SHA-256");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }

    /**
     * A helper method to calculate SHA-1 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data to get hashed
     * @return SHA hash of data
     */
    public static byte[] sha1(byte[]... data) {
        return hash("SHA-1", data);
    }

    /**
     * @param length number of bytes to return
     * @return an array of the given size containing random bytes
     */
    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }

    /**
     * Calculates the proof of work. This might take a long time, depending on the hardware, message size and time to
     * live.
     *
     * @param object             to do the proof of work for
     * @param worker             doing the actual proof of work
     * @param nonceTrialsPerByte difficulty
     * @param extraBytes         bytes to add to the object size (makes it more difficult to send small messages)
     */
    public static void doProofOfWork(ObjectMessage object, ProofOfWorkEngine worker, long nonceTrialsPerByte,
                                     long extraBytes) {
        try {
            if (nonceTrialsPerByte < 1000) nonceTrialsPerByte = 1000;
            if (extraBytes < 1000) extraBytes = 1000;

            byte[] initialHash = getInitialHash(object);

            byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);

            byte[] nonce = worker.calculateNonce(initialHash, target);
            object.setNonce(nonce);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param object             to be checked
     * @param nonceTrialsPerByte difficulty
     * @param extraBytes         bytes to add to the object size
     * @throws InsufficientProofOfWorkException if proof of work doesn't check out (makes it more difficult to send small messages)
     */
    public static void checkProofOfWork(ObjectMessage object, long nonceTrialsPerByte, long extraBytes)
            throws IOException {
        byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);
        byte[] value = Security.doubleSha512(object.getNonce(), getInitialHash(object));
        if (Bytes.lt(target, value, 8)) {
            throw new InsufficientProofOfWorkException(target, value);
        }
    }

    private static byte[] getInitialHash(ObjectMessage object) throws IOException {
        return Security.sha512(object.getPayloadBytesWithoutNonce());
    }

    private static byte[] getProofOfWorkTarget(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) throws IOException {
        BigInteger TTL = BigInteger.valueOf(object.getExpiresTime() - UnixTime.now());
        LOG.debug("TTL: " + TTL + "s");
        BigInteger numerator = TWO.pow(64);
        BigInteger powLength = BigInteger.valueOf(object.getPayloadBytesWithoutNonce().length + extraBytes);
        BigInteger denominator = BigInteger.valueOf(nonceTrialsPerByte).multiply(powLength.add(powLength.multiply(TTL).divide(BigInteger.valueOf(2).pow(16))));
        return Bytes.expand(numerator.divide(denominator).toByteArray(), 8);
    }

    private static byte[] hash(String algorithm, byte[]... data) {
        MessageDigest mda = md(algorithm);
        for (byte[] d : data) {
            mda.update(d);
        }
        return mda.digest();
    }

    private static MessageDigest md(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm, "BC");
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates the MAC for a message (data)
     *
     * @param key_m the symmetric key used
     * @param data  the message data to calculate the MAC for
     * @return the MAC
     */
    public static byte[] mac(byte[] key_m, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256", "BC");
            mac.init(new SecretKeySpec(key_m, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new public key fom given private keys.
     *
     * @param version              of the public key / address
     * @param stream               of the address
     * @param privateSigningKey    private key used for signing
     * @param privateEncryptionKey private key used for encryption
     * @param nonceTrialsPerByte   proof of work difficulty
     * @param extraBytes           bytes to add for the proof of work (make it harder for small messages)
     * @param features             of the address
     * @return a public key object
     */
    public static Pubkey createPubkey(long version, long stream, byte[] privateSigningKey, byte[] privateEncryptionKey,
                                      long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
        return Factory.createPubkey(version, stream,
                createPublicKey(privateSigningKey).getEncoded(false),
                createPublicKey(privateEncryptionKey).getEncoded(false),
                nonceTrialsPerByte, extraBytes, features);
    }

    /**
     * @param privateKey private key as byte array
     * @return a public key corresponding to the given private key
     */
    public static ECPoint createPublicKey(byte[] privateKey) {
        return EC_CURVE_PARAMETERS.getG().multiply(keyToBigInt(privateKey)).normalize();
    }

    /**
     * @param privateKey private key as byte array
     * @return a big integer representation (unsigned) of the given bytes
     */
    public static BigInteger keyToBigInt(byte[] privateKey) {
        return new BigInteger(1, privateKey);
    }

    public static ECPoint keyToPoint(byte[] publicKey) {
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(publicKey, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(publicKey, 33, 65));
        return EC_CURVE_PARAMETERS.getCurve().createPoint(x, y);
    }

    public static ECPoint createPoint(byte[] x, byte[] y) {
        return EC_CURVE_PARAMETERS.getCurve().createPoint(
                new BigInteger(1, x),
                new BigInteger(1, y)
        );
    }

    /**
     * @param data      to check
     * @param signature the signature of the message
     * @param pubkey    the sender's public key
     * @return true if the signature is valid, false otherwise
     */
    public static boolean isSignatureValid(byte[] data, byte[] signature, Pubkey pubkey) {
        try {
            ECParameterSpec spec = new ECParameterSpec(
                    EC_CURVE_PARAMETERS.getCurve(),
                    EC_CURVE_PARAMETERS.getG(),
                    EC_CURVE_PARAMETERS.getN(),
                    EC_CURVE_PARAMETERS.getH(),
                    EC_CURVE_PARAMETERS.getSeed()
            );

            ECPoint Q = keyToPoint(pubkey.getSigningKey());
            KeySpec keySpec = new ECPublicKeySpec(Q, spec);
            PublicKey publicKey = KeyFactory.getInstance("ECDSA", "BC").generatePublic(keySpec);

            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculate the signature of data, using the given private key.
     *
     * @param data       to be signed
     * @param privateKey to be used for signing
     * @return the signature
     */
    public static byte[] getSignature(byte[] data, ch.dissem.bitmessage.entity.valueobject.PrivateKey privateKey) {
        try {
            ECParameterSpec spec = new ECParameterSpec(
                    EC_CURVE_PARAMETERS.getCurve(),
                    EC_CURVE_PARAMETERS.getG(),
                    EC_CURVE_PARAMETERS.getN(),
                    EC_CURVE_PARAMETERS.getH(),
                    EC_CURVE_PARAMETERS.getSeed()
            );

            BigInteger d = keyToBigInt(privateKey.getPrivateSigningKey());
            KeySpec keySpec = new ECPrivateKeySpec(d, spec);
            PrivateKey privKey = KeyFactory.getInstance("ECDSA", "BC").generatePrivate(keySpec);

            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initSign(privKey);
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a random number of type long
     */
    public static long randomNonce() {
        return RANDOM.nextLong();
    }
}
