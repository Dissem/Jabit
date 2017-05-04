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

package ch.dissem.bitmessage.utils;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.payload.V4Pubkey;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.factory.Factory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * If there's ever a need for this in production code, it should be rewritten to be more efficient.
 */
public class TestUtils {
    public static final Random RANDOM = new Random();

    public static byte[] int16(int number) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Encode.int16(number, out);
        return out.toByteArray();
    }

    public static ObjectMessage loadObjectMessage(int version, String resourceName) throws IOException {
        byte[] data = getBytes(resourceName);
        InputStream in = new ByteArrayInputStream(data);
        return Factory.getObjectMessage(version, in, data.length);
    }

    public static byte[] getBytes(String resourceName) throws IOException {
        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resourceName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = in.read(buffer);
        while (len != -1) {
            out.write(buffer, 0, len);
            len = in.read(buffer);
        }
        return out.toByteArray();
    }

    public static InventoryVector randomInventoryVector() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new InventoryVector(bytes);
    }

    public static InputStream getResource(String resourceName) {
        return TestUtils.class.getClassLoader().getResourceAsStream(resourceName);
    }

    public static BitmessageAddress loadIdentity(String address) throws IOException {
        PrivateKey privateKey = PrivateKey.read(TestUtils.getResource(address + ".privkey"));
        BitmessageAddress identity = new BitmessageAddress(privateKey);
        assertEquals(address, identity.getAddress());
        return identity;
    }

    public static BitmessageAddress loadContact() throws IOException, DecryptionFailedException {
        BitmessageAddress address = new BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h");
        ObjectMessage object = TestUtils.loadObjectMessage(3, "V4Pubkey.payload");
        object.decrypt(address.getPublicDecryptionKey());
        address.setPubkey((V4Pubkey) object.getPayload());
        return address;
    }

    public static void loadPubkey(BitmessageAddress address) throws IOException {
        byte[] bytes = getBytes(address.getAddress() + ".pubkey");
        Pubkey pubkey = Factory.readPubkey(address.getVersion(), address.getStream(), new ByteArrayInputStream(bytes), bytes.length, false);
        address.setPubkey(pubkey);
    }
}
