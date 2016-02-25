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

package ch.dissem.bitmessage.cryptography.bc;

import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.AbstractCryptography;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * As Spongycastle can't be used on the Oracle JVM, and Bouncycastle doesn't work properly on Android (thanks, Google),
 * this is the Bouncycastle implementation.
 */
public class BouncyCryptography extends AbstractCryptography {
    private static final X9ECParameters EC_CURVE_PARAMETERS = CustomNamedCurves.getByName("secp256k1");

    static {
        java.security.Security.addProvider(new BouncyCastleProvider());
    }

    public BouncyCryptography() {
        super("BC");
    }

    @Override
    public byte[] crypt(boolean encrypt, byte[] data, byte[] key_e, byte[] initializationVector) {
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

    @Override
    public byte[] createPublicKey(byte[] privateKey) {
        return EC_CURVE_PARAMETERS.getG().multiply(keyToBigInt(privateKey)).normalize().getEncoded(false);
    }

    private ECPoint keyToPoint(byte[] publicKey) {
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(publicKey, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(publicKey, 33, 65));
        return EC_CURVE_PARAMETERS.getCurve().createPoint(x, y);
    }

    @Override
    public boolean isSignatureValid(byte[] data, byte[] signature, Pubkey pubkey) {
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
        } catch (GeneralSecurityException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public byte[] getSignature(byte[] data, PrivateKey privateKey) {
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
            java.security.PrivateKey privKey = KeyFactory.getInstance("ECDSA", "BC").generatePrivate(keySpec);

            Signature sig = Signature.getInstance("ECDSA", "BC");
            sig.initSign(privKey);
            sig.update(data);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public byte[] multiply(byte[] K, byte[] r) {
        return keyToPoint(K).multiply(keyToBigInt(r)).normalize().getEncoded(false);
    }

    @Override
    public byte[] createPoint(byte[] x, byte[] y) {
        return EC_CURVE_PARAMETERS.getCurve().createPoint(
                new BigInteger(1, x),
                new BigInteger(1, y)
        ).getEncoded(false);
    }
}
