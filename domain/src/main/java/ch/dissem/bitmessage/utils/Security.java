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
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;

/**
 * Provides some methods to help with hashing and encryption.
 */
public class Security {
    public static final Logger LOG = LoggerFactory.getLogger(Security.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final ECGenParameterSpec EC_PARAMETERS = new ECGenParameterSpec("secp256k1");

    static {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] sha512(byte[]... data) {
        return hash("SHA-512", data);
    }

    public static byte[] doubleSha512(byte[]... data) {
        MessageDigest mda = md("SHA-512");
        for (byte[] d : data) {
            mda.update(d);
        }
        return mda.digest(mda.digest());
    }

    public static byte[] doubleSha512(byte[] data, int length) {
        MessageDigest mda = md("SHA-512");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }

    public static byte[] ripemd160(byte[]... data) {
        return hash("RIPEMD160", data);
    }

    public static byte[] doubleSha256(byte[] data, int length) {
        MessageDigest mda = md("SHA-256");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }

    public static byte[] sha1(byte[]... data) {
        return hash("SHA-1", data);
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }

    public static void doProofOfWork(ObjectMessage object, ProofOfWorkEngine worker, long nonceTrialsPerByte, long extraBytes) throws IOException {
        if (nonceTrialsPerByte < 1000) nonceTrialsPerByte = 1000;
        if (extraBytes < 1000) extraBytes = 1000;

        byte[] initialHash = getInitialHash(object);

        byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);

        byte[] nonce = worker.calculateNonce(initialHash, target);
        object.setNonce(nonce);
    }

    /**
     * @throws IOException if proof of work doesn't check out
     */
    public static void checkProofOfWork(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) throws IOException {
        if (Bytes.lt(
                getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes),
                Security.doubleSha512(object.getNonce(), getInitialHash(object)),
                8)) {
            throw new IOException("Insufficient proof of work");
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

    public static Pubkey createPubkey(long version, long stream, byte[] privateSigningKey, byte[] privateEncryptionKey,
                                      long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
//        ECPublicKeySpec pubKey = new ECPublicKeySpec(
//                ECPointUtil.decodePoint(curve, Hex.decode("025b6dc53bc61a2548ffb0f671472de6c9521a9d2d2534e65abfcbd5fe0c70")), // Q
//                EC_PARAMETERS);
//        byte[] publicSigningKey = EC_PARAMETERS.getG().multiply(keyToBigInt(privateSigningKey)).getEncoded(false);
//        byte[] publicEncryptionKey = EC_PARAMETERS.getG().multiply(keyToBigInt(privateEncryptionKey)).getEncoded(false);
//        return Factory.createPubkey(version, stream, // publicSigningKey, publicEncryptionKey,
//                Bytes.subArray(publicSigningKey, 1, publicSigningKey.length - 1),
//                Bytes.subArray(publicEncryptionKey, 1, publicEncryptionKey.length - 1),
//                nonceTrialsPerByte, extraBytes, features);
        return null;
    }

    private static byte[] createPublicKey(byte[] privateKey){
//        ECParameterSpec spec = new ECNamedCurveSpec(ECNamedCurveTable.getParameterSpec("prime239v1"));
//        ECPrivateKeySpec priKey = new ECPrivateKeySpec(
//                new BigInteger("876300101507107567501066130761671078357010671067781776716671676178726717"), // d
//                spec);
//        ECPublicKeySpec pubKey = new ECPublicKeySpec(
//                ECPointUtil.decodePoint(
//                        spec.getCurve(),
//                        Hex.decode("025b6dc53bc61a2548ffb0f671472de6c9521a9d2d2534e65abfcbd5fe0c70")), // Q
//                spec);
        return null;
    }

    private static BigInteger keyToBigInt(byte[] key) {
        return new BigInteger(1, key);
    }

    public static boolean isSignatureValid(byte[] bytesToSign, byte[] signature, Pubkey pubkey) {
//        ECPoint W = EC_CURVE.getCurve().decodePoint(pubkey.getSigningKey()); // TODO: probably this needs 0x04 added
        try {
            ECParameterSpec param = null;
//            KeySpec keySpec = new ECPublicKeySpec(W,param);;
//            PublicKey publicKey = KeyFactory.getInstance("ECDSA", "BC").generatePublic(keySpec);

            Signature sig = Signature.getInstance("ECDSA", "BC");
//            sig.initVerify(publicKey);
            sig.update(bytesToSign);
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
