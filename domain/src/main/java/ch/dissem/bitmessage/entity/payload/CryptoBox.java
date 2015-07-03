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

package ch.dissem.bitmessage.entity.payload;

import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.utils.*;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;

import static ch.dissem.bitmessage.entity.valueobject.PrivateKey.PRIVATE_KEY_SIZE;


public class CryptoBox implements Streamable {
    private static final Logger LOG = LoggerFactory.getLogger(CryptoBox.class);

    private final byte[] initializationVector;
    private final int curveType;
    private final ECPoint R;
    private final byte[] mac;
    private byte[] encrypted;

    public CryptoBox(Streamable data, byte[] encryptionKey) throws IOException {
        this(data, Security.keyToPoint(encryptionKey));
    }

    public CryptoBox(Streamable data, ECPoint K) throws IOException {
        curveType = 0x02CA;

        // 1. The destination public key is called K.
        // 2. Generate 16 random bytes using a secure random number generator. Call them IV.
        initializationVector = Security.randomBytes(16);

        // 3. Generate a new random EC key pair with private key called r and public key called R.
        byte[] r = Security.randomBytes(PRIVATE_KEY_SIZE);
        R = Security.createPublicKey(r);
        // 4. Do an EC point multiply with public key K and private key r. This gives you public key P.
        ECPoint P = K.multiply(Security.keyToBigInt(r)).normalize();
        byte[] X = P.getXCoord().getEncoded();
        // 5. Use the X component of public key P and calculate the SHA512 hash H.
        byte[] H = Security.sha512(X);
        // 6. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
        byte[] key_e = Arrays.copyOfRange(H, 0, 32);
        byte[] key_m = Arrays.copyOfRange(H, 32, 64);
        // 7. Pad the input text to a multiple of 16 bytes, in accordance to PKCS7.
        // 8. Encrypt the data with AES-256-CBC, using IV as initialization vector, key_e as encryption key and the padded input text as payload. Call the output cipher text.
        encrypted = crypt(true, Encode.bytes(data), key_e);
        // 9. Calculate a 32 byte MAC with HMACSHA256, using key_m as salt and IV + R + cipher text as data. Call the output MAC.
        mac = calculateMac(key_m);

        // The resulting data is: IV + R + cipher text + MAC
    }

    private CryptoBox(Builder builder) {
        initializationVector = builder.initializationVector;
        curveType = builder.curveType;
        R = Security.createPoint(builder.xComponent, builder.yComponent);
        encrypted = builder.encrypted;
        mac = builder.mac;
    }

    public static CryptoBox read(InputStream stream, int length) throws IOException {
        AccessCounter counter = new AccessCounter();
        return new Builder()
                .IV(Decode.bytes(stream, 16, counter))
                .curveType(Decode.uint16(stream, counter))
                .X(Decode.shortVarBytes(stream, counter))
                .Y(Decode.shortVarBytes(stream, counter))
                .encrypted(Decode.bytes(stream, length - counter.length() - 32))
                .MAC(Decode.bytes(stream, 32))
                .build();
    }

    /**
     * @param privateKey a private key, typically should be 32 bytes long
     * @return an InputStream yielding the decrypted data
     * @throws DecryptionFailedException if the payload can't be decrypted using this private key
     * @see <a href='https://bitmessage.org/wiki/Encryption#Decryption'>https://bitmessage.org/wiki/Encryption#Decryption</a>
     */
    public InputStream decrypt(byte[] privateKey) throws DecryptionFailedException {
        // 1. The private key used to decrypt is called k.
        BigInteger k = Security.keyToBigInt(privateKey);
        // 2. Do an EC point multiply with private key k and public key R. This gives you public key P.
        ECPoint P = R.multiply(k).normalize();
        // 3. Use the X component of public key P and calculate the SHA512 hash H.
        byte[] H = Security.sha512(P.getXCoord().getEncoded());
        // 4. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
        byte[] key_e = Arrays.copyOfRange(H, 0, 32);
        byte[] key_m = Arrays.copyOfRange(H, 32, 64);

        // 5. Calculate MAC' with HMACSHA256, using key_m as salt and IV + R + cipher text as data.
        // 6. Compare MAC with MAC'. If not equal, decryption will fail.
        if (!Arrays.equals(mac, calculateMac(key_m))) {
            throw new DecryptionFailedException();
        }

        // 7. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
        //    and the cipher text as payload. The output is the padded input text.
        return new ByteArrayInputStream(crypt(false, encrypted, key_e));
    }

    private byte[] calculateMac(byte[] key_m) {
        try {
            ByteArrayOutputStream macData = new ByteArrayOutputStream();
            writeWithoutMAC(macData);
            return Security.mac(key_m, macData.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] crypt(boolean encrypt, byte[] data, byte[] key_e) {
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), new PKCS7Padding());

        CipherParameters params = new ParametersWithIV(new KeyParameter(key_e), initializationVector);

        cipher.init(encrypt, params);

        byte[] buffer = new byte[cipher.getOutputSize(data.length)];
        int length = cipher.processBytes(data, 0, data.length, buffer, 0);
        try {
            length += cipher.doFinal(buffer, length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalArgumentException(e);
        }
        if (length < buffer.length) {
            return Arrays.copyOfRange(buffer, 0, length);
        }
        return buffer;
    }

    private void writeWithoutMAC(OutputStream out) throws IOException {
        out.write(initializationVector);
        Encode.int16(curveType, out);
        writeCoordinateComponent(out, R.getXCoord());
        writeCoordinateComponent(out, R.getYCoord());
        out.write(encrypted);
    }

    private void writeCoordinateComponent(OutputStream out, ECFieldElement coord) throws IOException {
        byte[] x = coord.getEncoded();
        int offset = Bytes.numberOfLeadingZeros(x);
        int length = x.length - offset;
        Encode.int16(length, out);
        out.write(x, offset, length);
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        writeWithoutMAC(stream);
        stream.write(mac);
    }

    public static final class Builder {
        private byte[] initializationVector;
        private int curveType;
        private byte[] xComponent;
        private byte[] yComponent;
        private byte[] encrypted;
        private byte[] mac;

        public Builder IV(byte[] initializationVector) {
            this.initializationVector = initializationVector;
            return this;
        }

        public Builder curveType(int curveType) {
            if (curveType != 0x2CA) LOG.debug("Unexpected curve type " + curveType);
            this.curveType = curveType;
            return this;
        }

        public Builder X(byte[] xComponent) {
            this.xComponent = xComponent;
            return this;
        }

        public Builder Y(byte[] yComponent) {
            this.yComponent = yComponent;
            return this;
        }

        private Builder encrypted(byte[] encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        public Builder MAC(byte[] mac) {
            this.mac = mac;
            return this;
        }

        public CryptoBox build() {
            return new CryptoBox(this);
        }
    }
}
