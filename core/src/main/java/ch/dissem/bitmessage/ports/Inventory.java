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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;

import java.util.List;

/**
 * The Inventory stores and retrieves objects, cleans up outdated objects and can tell which objects are still missing.
 */
public interface Inventory {
    /**
     * Returns the IVs of all valid objects we have for the given streams
     */
    List<InventoryVector> getInventory(long... streams);

    /**
     * Returns the IVs of all objects in the offer that we don't have already. Implementations are allowed to
     * ignore the streams parameter, but it must be set when calling this method.
     */
    List<InventoryVector> getMissing(List<InventoryVector> offer, long... streams);

    ObjectMessage getObject(InventoryVector vector);

    /**
     * This method is mainly used to search for public keys to newly added addresses or broadcasts from new
     * subscriptions.
     */
    List<ObjectMessage> getObjects(long stream, long version, ObjectType... types);

    void storeObject(ObjectMessage object);

    boolean contains(ObjectMessage object);

    /**
     * Deletes all objects that expired 5 minutes ago or earlier
     * (so we don't accidentally request objects we just deleted)
     */
    void cleanup();
}
