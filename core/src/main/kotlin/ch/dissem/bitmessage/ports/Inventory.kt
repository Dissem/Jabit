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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.valueobject.InventoryVector

/**
 * The Inventory stores and retrieves objects, cleans up outdated objects and can tell which objects are still missing.
 */
interface Inventory {
    /**
     * Returns the IVs of all valid objects we have for the given streams
     */
    fun getInventory(vararg streams: Long): List<InventoryVector>

    /**
     * Returns the IVs of all objects in the offer that we don't have already. Implementations are allowed to
     * ignore the streams parameter, but it must be set when calling this method.
     */
    fun getMissing(offer: List<InventoryVector>, vararg streams: Long): List<InventoryVector>

    fun getObject(vector: InventoryVector): ObjectMessage?

    /**
     * This method is mainly used to search for public keys to newly added addresses or broadcasts from new
     * subscriptions.
     */
    fun getObjects(stream: Long, version: Long, vararg types: ObjectType): List<ObjectMessage>

    fun storeObject(objectMessage: ObjectMessage)

    operator fun contains(objectMessage: ObjectMessage): Boolean

    /**
     * Deletes all objects that expired 5 minutes ago or earlier
     * (so we don't accidentally request objects we just deleted)
     */
    fun cleanup()
}
