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

package ch.dissem.bitmessage.inventory;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.ports.Inventory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by chris on 06.04.15.
 */
public class SimpleInventory implements Inventory {
    @Override
    public List<InventoryVector> getInventory(long... streams) {
        return new LinkedList<>();
    }

    @Override
    public List<InventoryVector> getMissing(List<InventoryVector> offer, long... streams) {
        return offer;
    }

    @Override
    public ObjectMessage getObject(InventoryVector vector) {
        throw new NotImplementedException();
    }

    @Override
    public List<ObjectMessage> getObjects(long stream, long version, ObjectType type) {
        return new LinkedList<>();
    }

    @Override
    public void storeObject(ObjectMessage object) {
        throw new NotImplementedException();
    }

    @Override
    public void cleanup() {
        throw new NotImplementedException();
    }
}
