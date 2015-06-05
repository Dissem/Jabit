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

package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.ports.Inventory;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.entity.payload.ObjectType.GET_PUBKEY;
import static ch.dissem.bitmessage.entity.payload.ObjectType.MSG;
import static ch.dissem.bitmessage.utils.UnixTime.DAY;
import static ch.dissem.bitmessage.utils.UnixTime.now;
import static org.junit.Assert.*;

public class JdbcInventoryTest {
    private TestJdbcConfig config;
    private Inventory inventory;

    private InventoryVector inventoryVector1;
    private InventoryVector inventoryVector2;
    private InventoryVector inventoryVectorIgnore;

    @Before
    public void setUp() throws Exception {
        config = new TestJdbcConfig();
        config.reset();

        inventory = new JdbcInventory(config);

        ObjectMessage object1 = getObjectMessage(1, 300, getGetPubkey());
        inventoryVector1 = object1.getInventoryVector();
        inventory.storeObject(object1);

        ObjectMessage object2 = getObjectMessage(2, 300, getGetPubkey());
        inventoryVector2 = object2.getInventoryVector();
        inventory.storeObject(object2);

        ObjectMessage ignore = getObjectMessage(1, -1 * DAY, getGetPubkey());
        inventoryVectorIgnore = ignore.getInventoryVector();
        inventory.storeObject(ignore);
    }

    @Test
    public void testGetInventory() throws Exception {
        List<InventoryVector> inventoryVectors = inventory.getInventory(1);
        assertEquals(1, inventoryVectors.size());

        inventoryVectors = inventory.getInventory(2);
        assertEquals(1, inventoryVectors.size());
    }

    @Test
    public void testGetMissing() throws Exception {
        InventoryVector newIV = getObjectMessage(1, 200, getGetPubkey()).getInventoryVector();
        List<InventoryVector> offer = new LinkedList<>();
        offer.add(newIV);
        offer.add(inventoryVector1);
        List<InventoryVector> missing = inventory.getMissing(offer, 1, 2);
        assertEquals(1, missing.size());
        assertEquals(newIV, missing.get(0));
    }

    @Test
    public void testGetObject() throws Exception {
        ObjectMessage object = inventory.getObject(inventoryVectorIgnore);
        assertNotNull(object);
        assertEquals(1, object.getStream());
        assertEquals(inventoryVectorIgnore, object.getInventoryVector());
    }

    @Test
    public void testGetObjects() throws Exception {
        List<ObjectMessage> objects = inventory.getObjects(1, 4);
        assertEquals(2, objects.size());

        objects = inventory.getObjects(1, 4, GET_PUBKEY);
        assertEquals(2, objects.size());

        objects = inventory.getObjects(1, 4, MSG);
        assertEquals(0, objects.size());
    }

    @Test
    public void testStoreObject() throws Exception {
        ObjectMessage object = getObjectMessage(5, 0, getGetPubkey());
        inventory.storeObject(object);

        assertNotNull(inventory.getObject(object.getInventoryVector()));
    }

    @Test
    public void testCleanup() throws Exception {
        assertNotNull(inventory.getObject(inventoryVectorIgnore));
        inventory.cleanup();
        assertNull(inventory.getObject(inventoryVectorIgnore));
    }

    private ObjectMessage getObjectMessage(long stream, long TTL, ObjectPayload payload) {
        return new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(now(+TTL))
                .stream(stream)
                .payload(payload)
                .build();
    }

    private GetPubkey getGetPubkey() {
        return new GetPubkey(new BitmessageAddress("BM-2cW7cD5cDQJDNkE7ibmyTxfvGAmnPqa9Vt"));
    }
}