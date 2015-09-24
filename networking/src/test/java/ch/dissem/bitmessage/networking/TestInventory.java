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

package ch.dissem.bitmessage.networking;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.ports.Inventory;
import ch.dissem.bitmessage.utils.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestInventory implements Inventory {
    private final Map<InventoryVector, ObjectMessage> inventory;

    public TestInventory() {
        this.inventory = new HashMap<>();
    }

    @Override
    public List<InventoryVector> getInventory(long... streams) {
        return new ArrayList<>(inventory.keySet());
    }

    @Override
    public List<InventoryVector> getMissing(List<InventoryVector> offer, long... streams) {
        return offer;
    }

    @Override
    public ObjectMessage getObject(InventoryVector vector) {
        return inventory.get(vector);
    }

    @Override
    public List<ObjectMessage> getObjects(long stream, long version, ObjectType... types) {
        return new ArrayList<>(inventory.values());
    }

    @Override
    public void storeObject(ObjectMessage object) {
        inventory.put(object.getInventoryVector(), object);
    }

    @Override
    public void cleanup() {

    }

    public void init(String... resources) throws IOException {
        inventory.clear();
        for (String resource : resources) {
            int version = Integer.parseInt(resource.substring(1, 2));
            ObjectMessage obj = TestUtils.loadObjectMessage(version, resource);
            inventory.put(obj.getInventoryVector(), obj);
        }
    }
}
