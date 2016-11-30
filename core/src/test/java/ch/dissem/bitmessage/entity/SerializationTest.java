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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.TestBase;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;
import static org.junit.Assert.*;

public class SerializationTest extends TestBase {
    @Test
    public void ensureGetPubkeyIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V2GetPubkey.payload", 2, GetPubkey.class);
        doTest("V3GetPubkey.payload", 2, GetPubkey.class);
        doTest("V4GetPubkey.payload", 2, GetPubkey.class);
    }

    @Test
    public void ensureV2PubkeyIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V2Pubkey.payload", 2, V2Pubkey.class);
    }

    @Test
    public void ensureV3PubkeyIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V3Pubkey.payload", 3, V3Pubkey.class);
    }

    @Test
    public void ensureV4PubkeyIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V4Pubkey.payload", 4, V4Pubkey.class);
    }

    @Test
    public void ensureV1MsgIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V1Msg.payload", 1, Msg.class);
    }

    @Test
    public void ensureV4BroadcastIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V4Broadcast.payload", 4, V4Broadcast.class);
    }

    @Test
    public void ensureV5BroadcastIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V5Broadcast.payload", 5, V5Broadcast.class);
    }

    @Test
    public void ensureUnknownDataIsDeserializedAndSerializedCorrectly() throws IOException {
        doTest("V1MsgStrangeData.payload", 1, GenericPayload.class);
    }

    @Test
    public void ensurePlaintextIsSerializedAndDeserializedCorrectly() throws Exception {
        Plaintext p1 = new Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message("Subject", "Message")
            .ackData("ackMessage".getBytes())
            .signature(new byte[0])
            .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p1.write(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Plaintext p2 = Plaintext.read(MSG, in);

        // Received is automatically set on deserialization, so we'll need to set it to 0
        Field received = Plaintext.class.getDeclaredField("received");
        received.setAccessible(true);
        received.set(p2, 0L);

        assertEquals(p1, p2);
    }

    @Test
    public void ensurePlaintextWithExtendedEncodingIsSerializedAndDeserializedCorrectly() throws Exception {
        Plaintext p1 = new Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message(new ExtendedEncoding.Builder().message()
                .subject("Subject")
                .body("Message")
                .build())
            .ackData("ackMessage".getBytes())
            .signature(new byte[0])
            .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p1.write(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Plaintext p2 = Plaintext.read(MSG, in);

        // Received is automatically set on deserialization, so we'll need to set it to 0
        Field received = Plaintext.class.getDeclaredField("received");
        received.setAccessible(true);
        received.set(p2, 0L);

        assertEquals(p1, p2);
    }

    @Test
    public void ensurePlaintextWithAckMessageIsSerializedAndDeserializedCorrectly() throws Exception {
        Plaintext p1 = new Plaintext.Builder(MSG)
            .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .to(TestUtils.loadContact())
            .message("Subject", "Message")
            .ackData("ackMessage".getBytes())
            .signature(new byte[0])
            .build();
        ObjectMessage ackMessage1 = p1.getAckMessage();
        assertNotNull(ackMessage1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p1.write(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Plaintext p2 = Plaintext.read(MSG, in);

        // Received is automatically set on deserialization, so we'll need to set it to 0
        Field received = Plaintext.class.getDeclaredField("received");
        received.setAccessible(true);
        received.set(p2, 0L);

        assertEquals(p1, p2);
        assertEquals(ackMessage1, p2.getAckMessage());
    }

    @Test
    public void ensureNetworkMessageIsSerializedAndDeserializedCorrectly() throws Exception {
        ArrayList<InventoryVector> ivs = new ArrayList<>(50000);
        for (int i = 0; i < 50000; i++) {
            ivs.add(new InventoryVector(cryptography().randomBytes(32)));
        }

        Inv inv = new Inv.Builder().inventory(ivs).build();
        NetworkMessage before = new NetworkMessage(inv);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        before.write(out);

        NetworkMessage after = Factory.getNetworkMessage(3, new ByteArrayInputStream(out.toByteArray()));
        assertNotNull(after);
        Inv invAfter = (Inv) after.getPayload();
        assertEquals(ivs, invAfter.getInventory());
    }

    private void doTest(String resourceName, int version, Class<?> expectedPayloadType) throws IOException {
        byte[] data = TestUtils.getBytes(resourceName);
        InputStream in = new ByteArrayInputStream(data);
        ObjectMessage object = Factory.getObjectMessage(version, in, data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNotNull(object);
        object.write(out);
        assertArrayEquals(data, out.toByteArray());
        assertEquals(expectedPayloadType.getCanonicalName(), object.getPayload().getClass().getCanonicalName());
    }

    @Test
    public void ensureSystemSerializationWorks() throws Exception {
        Plaintext plaintext = new Plaintext.Builder(MSG)
            .from(TestUtils.loadContact())
            .to(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
            .labels(Collections.singletonList(new Label("Test", Label.Type.INBOX, 0)))
            .message("Test", "Test Test.\nTest")
            .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(plaintext);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(in);
        assertEquals(plaintext, ois.readObject());
    }
}
