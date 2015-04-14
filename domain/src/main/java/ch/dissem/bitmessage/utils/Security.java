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
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Provides some methods to help with hashing and encryption.
 */
public class Security {
    public static final Logger LOG = LoggerFactory.getLogger(Security.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger TWO = BigInteger.valueOf(2);

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

    public static byte[] ripemd160(byte[]... data) {
        return hash("RIPEMD160", data);
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
        byte[] initialHash = getInitialHash(object);

        byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);

        byte[] nonce = worker.calculateNonce(initialHash, target, nonceTrialsPerByte, extraBytes);
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
        BigInteger TTL = BigInteger.valueOf(object.getExpiresTime() - (System.currentTimeMillis() / 1000));
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
}
