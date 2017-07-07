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

import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.*
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.TestBase
import ch.dissem.bitmessage.utils.TestUtils
import org.hamcrest.Matchers.`is`
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

class SerializationTest : TestBase() {
    @Test
    fun `ensure GetPubkey is deserialized and serialized correctly`() {
        doTest("V2GetPubkey.payload", 2, GetPubkey::class.java)
        doTest("V3GetPubkey.payload", 2, GetPubkey::class.java)
        doTest("V4GetPubkey.payload", 2, GetPubkey::class.java)
    }

    @Test
    fun `ensure V2Pubkey is deserialized and serialized correctly`() {
        doTest("V2Pubkey.payload", 2, V2Pubkey::class.java)
    }

    @Test
    fun `ensure V3Pubkey is deserialized and serialized correctly`() {
        doTest("V3Pubkey.payload", 3, V3Pubkey::class.java)
    }

    @Test
    fun `ensure V4Pubkey is deserialized and serialized correctly`() {
        doTest("V4Pubkey.payload", 4, V4Pubkey::class.java)
    }

    @Test
    fun `ensure V1 msg is deserialized and serialized correctly`() {
        doTest("V1Msg.payload", 1, Msg::class.java)
    }

    @Test
    fun `ensure V4Broadcast is deserialized and serialized correctly`() {
        doTest("V4Broadcast.payload", 4, V4Broadcast::class.java)
    }

    @Test
    fun `ensure V5Broadcast is deserialized and serialized correctly`() {
        doTest("V5Broadcast.payload", 5, V5Broadcast::class.java)
    }

    @Test
    fun `ensure unknown data is deserialized and serialized correctly`() {
        doTest("V1MsgStrangeData.payload", 1, GenericPayload::class.java)
    }

    @Test
    fun `ensure plaintext is serialized and deserialized correctly`() {
        val expected = Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message("Subject", "Message")
            .ackData("ackMessage".toByteArray())
            .signature(ByteArray(0))
            .build()
        val out = ByteArrayOutputStream()
        expected.write(out)
        val `in` = ByteArrayInputStream(out.toByteArray())
        val actual = Plaintext.read(MSG, `in`)

        // Received is automatically set on deserialization, so we'll need to set it to null
        val received = Plaintext::class.java.getDeclaredField("received")
        received.isAccessible = true
        received.set(actual, null)

        assertThat(expected, `is`(actual))
    }

    @Test
    fun `ensure plaintext with extended encoding is serialized and deserialized correctly`() {
        val expected = Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message(Message.Builder()
                .subject("Subject")
                .body("Message")
                .build())
            .ackData("ackMessage".toByteArray())
            .signature(ByteArray(0))
            .build()
        val out = ByteArrayOutputStream()
        expected.write(out)
        val `in` = ByteArrayInputStream(out.toByteArray())
        val actual = Plaintext.read(MSG, `in`)

        // Received is automatically set on deserialization, so we'll need to set it to null
        val received = Plaintext::class.java.getDeclaredField("received")
        received.isAccessible = true
        received.set(actual, null)

        assertEquals(expected, actual)
    }

    @Test
    fun `ensure plaintext with ack message is serialized and deserialized correctly`() {
        val expected = Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message("Subject", "Message")
            .ackData("ackMessage".toByteArray())
            .signature(ByteArray(0))
            .build()
        val ackMessage1 = expected.ackMessage
        assertNotNull(ackMessage1)

        val out = ByteArrayOutputStream()
        expected.write(out)
        val `in` = ByteArrayInputStream(out.toByteArray())
        val actual = Plaintext.read(MSG, `in`)

        // Received is automatically set on deserialization, so we'll need to set it to null
        val received = Plaintext::class.java.getDeclaredField("received")
        received.isAccessible = true
        received.set(actual, null)

        assertEquals(expected, actual)
        assertEquals(ackMessage1, actual.ackMessage)
    }

    @Test
    fun `ensure network message is serialized and deserialized correctly`() {
        val ivs = ArrayList<InventoryVector>(50000)
        for (i in 0..49999) {
            ivs.add(TestUtils.randomInventoryVector())
        }

        val inv = Inv(ivs)
        val before = NetworkMessage(inv)
        val out = ByteArrayOutputStream()
        before.write(out)

        val after = Factory.getNetworkMessage(3, ByteArrayInputStream(out.toByteArray()))
        assertNotNull(after)
        val invAfter = after!!.payload as Inv
        assertEquals(ivs, invAfter.inventory)
    }

    private fun doTest(resourceName: String, version: Int, expectedPayloadType: Class<*>) {
        val data = TestUtils.getBytes(resourceName)
        val `in` = ByteArrayInputStream(data)
        val objectMessage = Factory.getObjectMessage(version, `in`, data.size)
        val out = ByteArrayOutputStream()
        assertNotNull(objectMessage)
        objectMessage!!.write(out)
        assertArrayEquals(data, out.toByteArray())
        assertEquals(expectedPayloadType.canonicalName, objectMessage.payload.javaClass.canonicalName)
    }

    @Test
    fun `ensure system serialization works`() {
        val plaintext = Plaintext.Builder(MSG)
            .from(TestUtils.loadContact())
            .to(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .labels(listOf(Label("Test", Label.Type.INBOX, 0)))
            .message("Test", "Test Test.\nTest")
            .build()
        val out = ByteArrayOutputStream()
        val oos = ObjectOutputStream(out)
        oos.writeObject(plaintext)

        val `in` = ByteArrayInputStream(out.toByteArray())
        val ois = ObjectInputStream(`in`)
        assertEquals(plaintext, ois.readObject())
    }
}
