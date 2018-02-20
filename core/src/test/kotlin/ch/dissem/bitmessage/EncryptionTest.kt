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

package ch.dissem.bitmessage

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.payload.CryptoBox
import ch.dissem.bitmessage.entity.payload.GenericPayload
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EncryptionTest : TestBase() {
    @Test
    fun `ensure decrypted data is same as before encryption`() {
        val before = GenericPayload(0, 1, cryptography().randomBytes(100))

        val privateKey = PrivateKey(false, 1, 1000, 1000)
        val cryptoBox = CryptoBox(before, privateKey.pubkey.encryptionKey)

        val after = GenericPayload.read(0, 1, cryptoBox.decrypt(privateKey.privateEncryptionKey), 100)

        assertEquals(before, after)
    }

    @Test
    fun `ensure message can be decrypted`() {
        val privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"))
        val identity = BitmessageAddress(privateKey)
        assertEquals("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8", identity.address)

        val (_, _, payload) = TestUtils.loadObjectMessage(3, "V1Msg.payload")
        val msg = payload as Msg
        msg.decrypt(privateKey.privateEncryptionKey)
        assertNotNull(msg.plaintext)
        assertEquals("Test", msg.plaintext?.subject)
        assertEquals("Hallo, das ist ein Test von der v4-Adresse", msg.plaintext?.text)
    }
}
