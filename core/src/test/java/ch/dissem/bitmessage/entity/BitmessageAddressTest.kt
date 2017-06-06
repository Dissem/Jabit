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

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature.INCLUDE_DESTINATION
import ch.dissem.bitmessage.entity.payload.V4Pubkey
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.utils.*
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.*

class BitmessageAddressTest : TestBase() {
    @Test
    fun `ensure feature flag is calculated correctly`() {
        Assert.assertEquals(1, Pubkey.Feature.bitfield(DOES_ACK).toLong())
        assertEquals(2, Pubkey.Feature.bitfield(INCLUDE_DESTINATION).toLong())
        assertEquals(3, Pubkey.Feature.bitfield(DOES_ACK, INCLUDE_DESTINATION).toLong())
    }

    @Test
    fun `ensure base58 decodes correctly`() {
        assertHexEquals("800C28FCA386C7A227600B2FE50B7CAE11EC86D3BF1FBE471BE89827E19D72AA1D507A5B8D",
                Base58.decode("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ"))
    }

    @Test
    fun `ensure address stays same`() {
        val address = "BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ"
        assertEquals(address, BitmessageAddress(address).toString())
    }

    @Test
    fun `ensure stream and version are parsed`() {
        var address = BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")
        assertEquals(1, address.stream)
        assertEquals(3, address.version)

        address = BitmessageAddress("BM-87hJ99tPAXxtetvnje7Z491YSvbEtBJVc5e")
        assertEquals(1, address.stream)
        assertEquals(4, address.version)
    }

    @Test
    fun `ensure identity can be created`() {
        val address = BitmessageAddress(PrivateKey(false, 1, 1000, 1000, DOES_ACK))
        assertNotNull(address.pubkey)
        assertTrue(address.has(DOES_ACK))
    }

    @Test
    fun `ensure V2Pubkey can be imported`() {
        val (_, _, payload) = TestUtils.loadObjectMessage(2, "V2Pubkey.payload")
        val pubkey = payload as Pubkey
        val address = BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT")
        try {
            address.pubkey = pubkey
        } catch (e: Exception) {
            fail(e.message)
        }

    }

    @Test
    fun `ensure V3Pubkey can be imported`() {
        val address = BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")
        Assert.assertArrayEquals(Bytes.fromHex("007402be6e76c3cb87caa946d0c003a3d4d8e1d5"), address.ripe)

        val `object` = TestUtils.loadObjectMessage(3, "V3Pubkey.payload")
        val pubkey = `object`.payload as Pubkey
        assertTrue(`object`.isSignatureValid(pubkey))
        try {
            address.pubkey = pubkey
        } catch (e: Exception) {
            fail(e.message)
        }

        assertArrayEquals(Bytes.fromHex("007402be6e76c3cb87caa946d0c003a3d4d8e1d5"), pubkey.ripe)
        assertTrue(address.has(DOES_ACK))
    }

    @Test
    fun `ensure V4Pubkey can be imported`() {
        val address = BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        val `object` = TestUtils.loadObjectMessage(4, "V4Pubkey.payload")
        `object`.decrypt(address.publicDecryptionKey)
        val pubkey = `object`.payload as V4Pubkey
        assertTrue(`object`.isSignatureValid(pubkey))
        try {
            address.pubkey = pubkey
        } catch (e: Exception) {
            fail(e.message)
        }

        assertTrue(address.has(DOES_ACK))
    }

    @Test
    fun `ensure V3 identity can be imported`() {
        val address_string = "BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn"
        assertEquals(3, BitmessageAddress(address_string).version)
        assertEquals(1, BitmessageAddress(address_string).stream)

        val privsigningkey = getSecret("5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9")
        val privencryptionkey = getSecret("5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck")

        println("\n\n" + Strings.hex(privsigningkey) + "\n\n")

        val address = BitmessageAddress(PrivateKey(privsigningkey, privencryptionkey,
                Singleton.cryptography().createPubkey(3, 1, privsigningkey, privencryptionkey, 320, 14000)))
        assertEquals(address_string, address.address)
    }

    @Test
    fun `ensure V4 identity can be imported`() {
        assertEquals(4, BitmessageAddress("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke").version)
        val privsigningkey = getSecret("5KMWqfCyJZGFgW6QrnPJ6L9Gatz25B51y7ErgqNr1nXUVbtZbdU")
        val privencryptionkey = getSecret("5JXXWEuhHQEPk414SzEZk1PHDRi8kCuZd895J7EnKeQSahJPxGz")
        val address = BitmessageAddress(PrivateKey(privsigningkey, privencryptionkey,
                Singleton.cryptography().createPubkey(4, 1, privsigningkey, privencryptionkey, 320, 14000)))
        assertEquals("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke", address.address)
    }

    private fun assertHexEquals(hex: String, bytes: ByteArray) {
        assertEquals(hex.toLowerCase(), Strings.hex(bytes).toLowerCase())
    }

    private fun getSecret(walletImportFormat: String): ByteArray {
        val bytes = Base58.decode(walletImportFormat)
        if (bytes[0] != 0x80.toByte())
            throw IOException("Unknown format: 0x80 expected as first byte, but secret " + walletImportFormat + " was " + bytes[0])
        if (bytes.size != 37)
            throw IOException("Unknown format: 37 bytes expected, but secret " + walletImportFormat + " was " + bytes.size + " long")

        val hash = Singleton.cryptography().doubleSha256(bytes, 33)
        for (i in 0..3) {
            if (hash[i] != bytes[33 + i]) throw IOException("Hash check failed for secret " + walletImportFormat)
        }
        return Arrays.copyOfRange(bytes, 1, 33)
    }
}
