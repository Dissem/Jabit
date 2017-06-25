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

package ch.dissem.bitmessage.cryptography.bc

import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.ports.AbstractCryptography
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.KeyFactory
import java.util.*

/**
 * As Spongycastle can't be used on the Oracle JVM, and Bouncycastle doesn't work properly on Android (thanks, Google),
 * this is the Bouncycastle implementation.
 */
class BouncyCryptography : AbstractCryptography(BouncyCastleProvider()) {

    override fun crypt(encrypt: Boolean, data: ByteArray, key_e: ByteArray, initializationVector: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(
            CBCBlockCipher(AESEngine()),
            PKCS7Padding()
        )
        val params = ParametersWithIV(KeyParameter(key_e), initializationVector)

        cipher.init(encrypt, params)

        val buffer = ByteArray(cipher.getOutputSize(data.size))
        var length = cipher.processBytes(data, 0, data.size, buffer, 0)
        try {
            length += cipher.doFinal(buffer, length)
        } catch (e: InvalidCipherTextException) {
            throw IllegalArgumentException(e)
        }

        if (length < buffer.size) {
            return Arrays.copyOfRange(buffer, 0, length)
        }
        return buffer
    }

    override fun createPublicKey(privateKey: ByteArray): ByteArray {
        return EC_CURVE_PARAMETERS.g.multiply(keyToBigInt(privateKey)).normalize().getEncoded(false)
    }

    private fun keyToPoint(publicKey: ByteArray): ECPoint {
        val x = BigInteger(1, Arrays.copyOfRange(publicKey, 1, 33))
        val y = BigInteger(1, Arrays.copyOfRange(publicKey, 33, 65))
        return EC_CURVE_PARAMETERS.curve.createPoint(x, y)
    }

    override fun isSignatureValid(data: ByteArray, signature: ByteArray, pubkey: Pubkey): Boolean {
        val spec = ECParameterSpec(
            EC_CURVE_PARAMETERS.curve,
            EC_CURVE_PARAMETERS.g,
            EC_CURVE_PARAMETERS.n,
            EC_CURVE_PARAMETERS.h,
            EC_CURVE_PARAMETERS.seed
        )

        val Q = keyToPoint(pubkey.signingKey)
        val keySpec = ECPublicKeySpec(Q, spec)
        val publicKey = KeyFactory.getInstance(ALGORITHM_ECDSA, provider).generatePublic(keySpec)

        return doCheckSignature(data, signature, publicKey)
    }

    override fun getSignature(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val spec = ECParameterSpec(
            EC_CURVE_PARAMETERS.curve,
            EC_CURVE_PARAMETERS.g,
            EC_CURVE_PARAMETERS.n,
            EC_CURVE_PARAMETERS.h,
            EC_CURVE_PARAMETERS.seed
        )

        val d = keyToBigInt(privateKey.privateSigningKey)
        val keySpec = ECPrivateKeySpec(d, spec)
        val privKey = KeyFactory.getInstance(ALGORITHM_ECDSA, provider)
            .generatePrivate(keySpec)

        return doSign(data, privKey)
    }

    override fun multiply(k: ByteArray, r: ByteArray): ByteArray {
        return keyToPoint(k).multiply(keyToBigInt(r)).normalize().getEncoded(false)
    }

    override fun createPoint(x: ByteArray, y: ByteArray): ByteArray {
        return EC_CURVE_PARAMETERS.curve.createPoint(
            BigInteger(1, x),
            BigInteger(1, y)
        ).getEncoded(false)
    }

    companion object {
        private val EC_CURVE_PARAMETERS = CustomNamedCurves.getByName("secp256k1")
    }
}
