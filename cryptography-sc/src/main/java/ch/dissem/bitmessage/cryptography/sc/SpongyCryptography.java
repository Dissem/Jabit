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

package ch.dissem.bitmessage.cryptography.sc;

import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.AbstractCryptography;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.ec.CustomNamedCurves;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PKCS7Padding;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;
import org.spongycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * As Spongycastle can't be used on the Oracle JVM, and Bouncycastle doesn't work properly on Android (thanks, Google),
 * this is the Spongycastle implementation.
 */
public class SpongyCryptography extends AbstractCryptography {
    private static final X9ECParameters EC_CURVE_PARAMETERS = CustomNamedCurves.getByName("secp256k1");

    public SpongyCryptography() {
        super(new BouncyCastleProvider());
    }

    @Override
    public byte[] crypt(boolean encrypt, byte[] data, byte[] key_e, byte[] initializationVector) {
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
            new CBCBlockCipher(new AESEngine()),
            new PKCS7Padding()
        );
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
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM_ECDSA, provider).generatePublic(keySpec);

            return doCheckSignature(data, signature, publicKey);
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
            java.security.PrivateKey privKey = KeyFactory.getInstance(ALGORITHM_ECDSA, provider)
                .generatePrivate(keySpec);

            return doSign(data, privKey);
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
