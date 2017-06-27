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
import ch.dissem.bitmessage.entity.payload.V4Broadcast
import ch.dissem.bitmessage.entity.payload.V5Broadcast
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecryptionTest : TestBase() {
    @Test
    fun `ensure V4Broadcast is decrypted correctly`() {
        val address = BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ")
        TestUtils.loadPubkey(address)
        val objectMessage = TestUtils.loadObjectMessage(5, "V4Broadcast.payload")
        val broadcast = objectMessage.payload as V4Broadcast
        broadcast.decrypt(address)
        assertEquals("Test-Broadcast", broadcast.plaintext?.subject)
        assertTrue(objectMessage.isSignatureValid(address.pubkey!!))
    }

    @Test
    fun `ensure V5Broadcast is decrypted correctly`() {
        val address = BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        TestUtils.loadPubkey(address)
        val objectMessage = TestUtils.loadObjectMessage(5, "V5Broadcast.payload")
        val broadcast = objectMessage.payload as V5Broadcast
        broadcast.decrypt(address)
        assertEquals("Test-Broadcast", broadcast.plaintext?.subject)
        assertTrue(objectMessage.isSignatureValid(address.pubkey!!))
    }
}
