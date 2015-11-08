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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Implements everything that isn't directly dependent on either Spongy- or Bouncycastle.
 */
public abstract class AbstractSecurity implements Security, InternalContext.ContextHolder {
    public static final Logger LOG = LoggerFactory.getLogger(Security.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger TWO = BigInteger.valueOf(2);

    private final String provider;
    private InternalContext context;

    protected AbstractSecurity(String provider) {
        this.provider = provider;
    }

    @Override
    public void setContext(InternalContext context) {
        this.context = context;
    }

    public byte[] sha512(byte[]... data) {
        return hash("SHA-512", data);
    }

    public byte[] doubleSha512(byte[]... data) {
        MessageDigest mda = md("SHA-512");
        for (byte[] d : data) {
            mda.update(d);
        }
        return mda.digest(mda.digest());
    }

    public byte[] doubleSha512(byte[] data, int length) {
        MessageDigest mda = md("SHA-512");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }

    public byte[] ripemd160(byte[]... data) {
        return hash("RIPEMD160", data);
    }

    public byte[] doubleSha256(byte[] data, int length) {
        MessageDigest mda = md("SHA-256");
        mda.update(data, 0, length);
        return mda.digest(mda.digest());
    }

    public byte[] sha1(byte[]... data) {
        return hash("SHA-1", data);
    }

    public byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }

    public void doProofOfWork(ObjectMessage object, long nonceTrialsPerByte,
                              long extraBytes, ProofOfWorkEngine.Callback callback) {
        try {
            if (nonceTrialsPerByte < 1000) nonceTrialsPerByte = 1000;
            if (extraBytes < 1000) extraBytes = 1000;

            byte[] initialHash = getInitialHash(object);

            byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);

            context.getProofOfWorkEngine().calculateNonce(initialHash, target, callback);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkProofOfWork(ObjectMessage object, long nonceTrialsPerByte, long extraBytes)
            throws IOException {
        byte[] target = getProofOfWorkTarget(object, nonceTrialsPerByte, extraBytes);
        byte[] value = doubleSha512(object.getNonce(), getInitialHash(object));
        if (Bytes.lt(target, value, 8)) {
            throw new InsufficientProofOfWorkException(target, value);
        }
    }

    private byte[] getInitialHash(ObjectMessage object) throws IOException {
        return sha512(object.getPayloadBytesWithoutNonce());
    }

    private byte[] getProofOfWorkTarget(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) throws IOException {
        BigInteger TTL = BigInteger.valueOf(object.getExpiresTime() - UnixTime.now());
        BigInteger numerator = TWO.pow(64);
        BigInteger powLength = BigInteger.valueOf(object.getPayloadBytesWithoutNonce().length + extraBytes);
        BigInteger denominator = BigInteger.valueOf(nonceTrialsPerByte).multiply(powLength.add(powLength.multiply(TTL).divide(BigInteger.valueOf(2).pow(16))));
        return Bytes.expand(numerator.divide(denominator).toByteArray(), 8);
    }

    private byte[] hash(String algorithm, byte[]... data) {
        MessageDigest mda = md(algorithm);
        for (byte[] d : data) {
            mda.update(d);
        }
        return mda.digest();
    }

    private MessageDigest md(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm, provider);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] mac(byte[] key_m, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256", provider);
            mac.init(new SecretKeySpec(key_m, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public Pubkey createPubkey(long version, long stream, byte[] privateSigningKey, byte[] privateEncryptionKey,
                               long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features) {
        return Factory.createPubkey(version, stream,
                createPublicKey(privateSigningKey),
                createPublicKey(privateEncryptionKey),
                nonceTrialsPerByte, extraBytes, features);
    }

    public BigInteger keyToBigInt(byte[] privateKey) {
        return new BigInteger(1, privateKey);
    }

    public long randomNonce() {
        return RANDOM.nextLong();
    }
}
