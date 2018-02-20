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

package ch.dissem.bitmessage.repository

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.GetPubkey
import ch.dissem.bitmessage.entity.payload.ObjectPayload
import ch.dissem.bitmessage.entity.payload.ObjectType.GET_PUBKEY
import ch.dissem.bitmessage.entity.payload.ObjectType.MSG
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.ports.Inventory
import ch.dissem.bitmessage.utils.UnixTime.DAY
import ch.dissem.bitmessage.utils.UnixTime.now
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class JdbcInventoryTest : TestBase() {
    private lateinit var config: TestJdbcConfig
    private lateinit var inventory: Inventory

    private lateinit var inventoryVector1: InventoryVector
    private lateinit var inventoryVector2: InventoryVector
    private lateinit var inventoryVectorIgnore: InventoryVector

    @BeforeEach
    fun setUp() {
        config = TestJdbcConfig()
        config.reset()

        inventory = JdbcInventory(config)

        val object1 = getObjectMessage(1, 300, getPubkey)
        inventoryVector1 = object1.inventoryVector
        inventory.storeObject(object1)

        val object2 = getObjectMessage(2, 300, getPubkey)
        inventoryVector2 = object2.inventoryVector
        inventory.storeObject(object2)

        val ignore = getObjectMessage(1, -1 * DAY, getPubkey)
        inventoryVectorIgnore = ignore.inventoryVector
        inventory.storeObject(ignore)
    }

    @Test
    fun `ensure inventory can be retrieved`() {
        var inventoryVectors = inventory.getInventory(1)
        assertEquals(1, inventoryVectors.size.toLong())

        inventoryVectors = inventory.getInventory(2)
        assertEquals(1, inventoryVectors.size.toLong())
    }

    @Test
    fun `ensure the IVs of missing objects are returned`() {
        val newIV = getObjectMessage(1, 200, getPubkey).inventoryVector
        val offer = LinkedList<InventoryVector>()
        offer.add(newIV)
        offer.add(inventoryVector1)
        val missing = inventory.getMissing(offer, 1, 2)
        assertEquals(1, missing.size.toLong())
        assertEquals(newIV, missing[0])
    }

    @Test
    fun `ensure single object can be retrieved`() {
        val `object` = inventory.getObject(inventoryVectorIgnore)
        assertNotNull(`object`)
        assertEquals(1, `object`!!.stream)
        assertEquals(inventoryVectorIgnore, `object`.inventoryVector)
    }

    @Test
    fun `ensure objects can be retrieved`() {
        var objects = inventory.getObjects(1, 4)
        assertEquals(2, objects.size.toLong())

        objects = inventory.getObjects(1, 4, GET_PUBKEY)
        assertEquals(2, objects.size.toLong())

        objects = inventory.getObjects(1, 4, MSG)
        assertEquals(0, objects.size.toLong())
    }

    @Test
    fun `ensure object can be stored`() {
        val `object` = getObjectMessage(5, 0, getPubkey)
        inventory.storeObject(`object`)

        assertNotNull(inventory.getObject(`object`.inventoryVector))
    }

    @Test
    fun `ensure contained objects are recognized`() {
        val `object` = getObjectMessage(5, 0, getPubkey)

        assertFalse(inventory.contains(`object`))

        inventory.storeObject(`object`)

        assertTrue(inventory.contains(`object`))
    }

    @Test
    fun `ensure inventory is cleaned up`() {
        assertNotNull(inventory.getObject(inventoryVectorIgnore))
        inventory.cleanup()
        assertNull(inventory.getObject(inventoryVectorIgnore))
    }

    private fun getObjectMessage(stream: Long, TTL: Long, payload: ObjectPayload): ObjectMessage {
        return ObjectMessage(
            nonce = ByteArray(8),
            expiresTime = now + TTL,
            stream = stream,
            payload = payload
        )
    }

    private val getPubkey: GetPubkey = GetPubkey(BitmessageAddress("BM-2cW7cD5cDQJDNkE7ibmyTxfvGAmnPqa9Vt"))
}
