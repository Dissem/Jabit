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
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by chris on 28.04.15.
 */
public class SerializationTest {
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

    private void doTest(String resourceName, int version, Class<?> expectedPayloadType) throws IOException {
        byte[] data = TestUtils.getBytes(resourceName);
        InputStream in = new ByteArrayInputStream(data);
        ObjectMessage object = Factory.getObjectMessage(version, in, data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        object.write(out);
        assertArrayEquals(data, out.toByteArray());
        assertEquals(expectedPayloadType.getCanonicalName(), object.getPayload().getClass().getCanonicalName());
    }
}
