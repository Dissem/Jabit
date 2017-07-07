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

import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.Assert.*
import org.junit.Test

class SignatureTest : TestBase() {
    @Test
    fun `ensure validation works`() {
        val objectMessage = TestUtils.loadObjectMessage(3, "V3Pubkey.payload")
        val pubkey = objectMessage.payload as Pubkey
        assertTrue(objectMessage.isSignatureValid(pubkey))
    }

    @Test
    fun `ensure signing works`() {
        val privateKey = PrivateKey(false, 1, 1000, 1000)

        val objectMessage = ObjectMessage.Builder()
            .objectType(ObjectType.PUBKEY)
            .stream(1)
            .payload(privateKey.pubkey)
            .build()
        objectMessage.sign(privateKey)

        assertTrue(objectMessage.isSignatureValid(privateKey.pubkey))
    }

    @Test
    fun `ensure message is properly signed`() {
        val identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")

        val objectMessage = TestUtils.loadObjectMessage(3, "V1Msg.payload")
        val msg = objectMessage.payload as Msg
        msg.decrypt(identity.privateKey!!.privateEncryptionKey)
        assertNotNull(msg.plaintext)
        assertEquals(TestUtils.loadContact().pubkey, msg.plaintext!!.from.pubkey)
        assertTrue(objectMessage.isSignatureValid(msg.plaintext!!.from.pubkey!!))
    }
}
