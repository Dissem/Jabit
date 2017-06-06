/*
 * Copyright 2017 Christian Basler
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

package ch.dissem.bitmessage.entity.payload

import ch.dissem.bitmessage.entity.Streamable
import ch.dissem.bitmessage.entity.valueobject.PrivateKey.Companion.PRIVATE_KEY_SIZE
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.utils.*
import ch.dissem.bitmessage.utils.Singleton.cryptography
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*


class CryptoBox : Streamable {

    private val initializationVector: ByteArray
    private val curveType: Int
    private val R: ByteArray
    private val mac: ByteArray
    private var encrypted: ByteArray

    constructor(data: Streamable, K: ByteArray) : this(Encode.bytes(data), K)

    constructor(data: ByteArray, K: ByteArray) {
        curveType = 0x02CA

        // 1. The destination public key is called K.
        // 2. Generate 16 random bytes using a secure random number generator. Call them IV.
        initializationVector = cryptography().randomBytes(16)

        // 3. Generate a new random EC key pair with private key called r and public key called R.
        val r = cryptography().randomBytes(PRIVATE_KEY_SIZE)
        R = cryptography().createPublicKey(r)
        // 4. Do an EC point multiply with public key K and private key r. This gives you public key P.
        val P = cryptography().multiply(K, r)
        val X = Points.getX(P)
        // 5. Use the X component of public key P and calculate the SHA512 hash H.
        val H = cryptography().sha512(X)
        // 6. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
        val key_e = Arrays.copyOfRange(H, 0, 32)
        val key_m = Arrays.copyOfRange(H, 32, 64)
        // 7. Pad the input text to a multiple of 16 bytes, in accordance to PKCS7.
        // 8. Encrypt the data with AES-256-CBC, using IV as initialization vector, key_e as encryption key and the padded input text as payload. Call the output cipher text.
        encrypted = cryptography().crypt(true, data, key_e, initializationVector)
        // 9. Calculate a 32 byte MAC with HMACSHA256, using key_m as salt and IV + R + cipher text as data. Call the output MAC.
        mac = calculateMac(key_m)

        // The resulting data is: IV + R + cipher text + MAC
    }

    private constructor(builder: Builder) {
        initializationVector = builder.initializationVector!!
        curveType = builder.curveType
        R = cryptography().createPoint(builder.xComponent!!, builder.yComponent!!)
        encrypted = builder.encrypted!!
        mac = builder.mac!!
    }

    /**
     * @param k a private key, typically should be 32 bytes long
     * *
     * @return an InputStream yielding the decrypted data
     * *
     * @throws DecryptionFailedException if the payload can't be decrypted using this private key
     * *
     * @see [https://bitmessage.org/wiki/Encryption.Decryption](https://bitmessage.org/wiki/Encryption.Decryption)
     */
    @Throws(DecryptionFailedException::class)
    fun decrypt(k: ByteArray): InputStream {
        // 1. The private key used to decrypt is called k.
        // 2. Do an EC point multiply with private key k and public key R. This gives you public key P.
        val P = cryptography().multiply(R, k)
        // 3. Use the X component of public key P and calculate the SHA512 hash H.
        val H = cryptography().sha512(Arrays.copyOfRange(P, 1, 33))
        // 4. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
        val key_e = Arrays.copyOfRange(H, 0, 32)
        val key_m = Arrays.copyOfRange(H, 32, 64)

        // 5. Calculate MAC' with HMACSHA256, using key_m as salt and IV + R + cipher text as data.
        // 6. Compare MAC with MAC'. If not equal, decryption will fail.
        if (!Arrays.equals(mac, calculateMac(key_m))) {
            throw DecryptionFailedException()
        }

        // 7. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
        //    and the cipher text as payload. The output is the padded input text.
        return ByteArrayInputStream(cryptography().crypt(false, encrypted, key_e, initializationVector))
    }

    private fun calculateMac(key_m: ByteArray): ByteArray {
        val macData = ByteArrayOutputStream()
        writeWithoutMAC(macData)
        return cryptography().mac(key_m, macData.toByteArray())
    }

    private fun writeWithoutMAC(out: OutputStream) {
        out.write(initializationVector)
        Encode.int16(curveType.toLong(), out)
        writeCoordinateComponent(out, Points.getX(R))
        writeCoordinateComponent(out, Points.getY(R))
        out.write(encrypted)
    }

    private fun writeCoordinateComponent(out: OutputStream, x: ByteArray) {
        val offset = Bytes.numberOfLeadingZeros(x)
        val length = x.size - offset
        Encode.int16(length.toLong(), out)
        out.write(x, offset, length)
    }

    private fun writeCoordinateComponent(buffer: ByteBuffer, x: ByteArray) {
        val offset = Bytes.numberOfLeadingZeros(x)
        val length = x.size - offset
        Encode.int16(length.toLong(), buffer)
        buffer.put(x, offset, length)
    }

    override fun write(out: OutputStream) {
        writeWithoutMAC(out)
        out.write(mac)
    }

    override fun write(buffer: ByteBuffer) {
        buffer.put(initializationVector)
        Encode.int16(curveType.toLong(), buffer)
        writeCoordinateComponent(buffer, Points.getX(R))
        writeCoordinateComponent(buffer, Points.getY(R))
        buffer.put(encrypted)
        buffer.put(mac)
    }

    class Builder {
        internal var initializationVector: ByteArray? = null
        internal var curveType: Int = 0
        internal var xComponent: ByteArray? = null
        internal var yComponent: ByteArray? = null
        internal var encrypted: ByteArray? = null
        internal var mac: ByteArray? = null

        fun IV(initializationVector: ByteArray): Builder {
            this.initializationVector = initializationVector
            return this
        }

        fun curveType(curveType: Int): Builder {
            if (curveType != 0x2CA) LOG.trace("Unexpected curve type " + curveType)
            this.curveType = curveType
            return this
        }

        fun X(xComponent: ByteArray): Builder {
            this.xComponent = xComponent
            return this
        }

        fun Y(yComponent: ByteArray): Builder {
            this.yComponent = yComponent
            return this
        }

        fun encrypted(encrypted: ByteArray): Builder {
            this.encrypted = encrypted
            return this
        }

        fun MAC(mac: ByteArray): Builder {
            this.mac = mac
            return this
        }

        fun build(): CryptoBox {
            return CryptoBox(this)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CryptoBox::class.java)

        fun read(stream: InputStream, length: Int): CryptoBox {
            val counter = AccessCounter()
            return Builder()
                .IV(Decode.bytes(stream, 16, counter))
                .curveType(Decode.uint16(stream, counter))
                .X(Decode.shortVarBytes(stream, counter))
                .Y(Decode.shortVarBytes(stream, counter))
                .encrypted(Decode.bytes(stream, length - counter.length() - 32))
                .MAC(Decode.bytes(stream, 32))
                .build()
        }
    }
}
