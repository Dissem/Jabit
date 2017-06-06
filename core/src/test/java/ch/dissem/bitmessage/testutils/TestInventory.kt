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

package ch.dissem.bitmessage.testutils

import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.ports.Inventory
import ch.dissem.bitmessage.utils.TestUtils
import java.util.*

class TestInventory : Inventory {
    private val inventory = HashMap<InventoryVector, ObjectMessage>()

    override fun getInventory(vararg streams: Long): List<InventoryVector> {
        return ArrayList(inventory.keys)
    }

    override fun getMissing(offer: List<InventoryVector>, vararg streams: Long): List<InventoryVector> {
        return offer
    }

    override fun getObject(vector: InventoryVector): ObjectMessage? {
        return inventory[vector]
    }

    override fun getObjects(stream: Long, version: Long, vararg types: ObjectType): List<ObjectMessage> {
        return ArrayList(inventory.values)
    }

    override fun storeObject(`object`: ObjectMessage) {
        inventory.put(`object`.inventoryVector, `object`)
    }

    override fun contains(`object`: ObjectMessage): Boolean {
        return inventory.containsKey(`object`.inventoryVector)
    }

    override fun cleanup() {

    }

    fun init(vararg resources: String) {
        inventory.clear()
        for (resource in resources) {
            val version = Integer.parseInt(resource.substring(1, 2))
            val obj = TestUtils.loadObjectMessage(version, resource)
            inventory.put(obj.inventoryVector, obj)
        }
    }
}
