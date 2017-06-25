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

import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.Inventory
import ch.dissem.bitmessage.utils.SqlStrings.join
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import ch.dissem.bitmessage.utils.UnixTime.now
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class JdbcInventory(config: JdbcConfig) : JdbcHelper(config), Inventory {

    private val cache = ConcurrentHashMap<Long, MutableMap<InventoryVector, Long>>()

    override fun getInventory(vararg streams: Long): List<InventoryVector> {
        val result = LinkedList<InventoryVector>()
        for (stream in streams) {
            getCache(stream).entries.stream()
                .filter { e -> e.value > now }
                .forEach { e -> result.add(e.key) }
        }
        return result
    }

    private fun getCache(stream: Long): MutableMap<InventoryVector, Long> {
        var result: MutableMap<InventoryVector, Long>? = cache[stream]
        if (result == null) {
            synchronized(cache) {
                if (cache[stream] == null) {
                    val map = ConcurrentHashMap<InventoryVector, Long>()
                    cache.put(stream, map)
                    result = map
                    try {
                        config.getConnection().use { connection ->
                            connection.createStatement().use { stmt ->
                                stmt.executeQuery("SELECT hash, expires FROM Inventory " +
                                    "WHERE expires > " + (now - 5 * MINUTE) + " AND stream = " + stream).use { rs ->
                                    while (rs.next()) {
                                        map.put(InventoryVector(rs.getBytes("hash")), rs.getLong("expires"))
                                    }
                                }
                            }
                        }
                    } catch (e: SQLException) {
                        LOG.error(e.message, e)
                    }
                }
            }
        }
        return result!!
    }

    override fun getMissing(offer: List<InventoryVector>, vararg streams: Long): List<InventoryVector> = offer - streams.flatMap { getCache(it).keys }

    override fun getObject(vector: InventoryVector): ObjectMessage? {
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT data, version FROM Inventory WHERE hash = X'$vector'").use { rs ->
                    if (rs.next()) {
                        val data = rs.getBlob("data")
                        return Factory.getObjectMessage(rs.getInt("version"), data.binaryStream, data.length().toInt())
                    } else {
                        LOG.info("Object requested that we don't have. IV: " + vector)
                        return null
                    }
                }
            }
        }
    }

    override fun getObjects(stream: Long, version: Long, vararg types: ObjectType): List<ObjectMessage> {
        val query = StringBuilder("SELECT data, version FROM Inventory WHERE 1=1")
        if (stream > 0) {
            query.append(" AND stream = ").append(stream)
        }
        if (version > 0) {
            query.append(" AND version = ").append(version)
        }
        if (types.isNotEmpty()) {
            query.append(" AND type IN (").append(join(*types)).append(')')
        }
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(query.toString()).use { rs ->
                    val result = LinkedList<ObjectMessage>()
                    while (rs.next()) {
                        val data = rs.getBlob("data")
                        result.add(Factory.getObjectMessage(rs.getInt("version"), data.binaryStream, data.length().toInt())!!)
                    }
                    return result
                }
            }
        }
    }

    override fun storeObject(objectMessage: ObjectMessage) {
        if (getCache(objectMessage.stream).containsKey(objectMessage.inventoryVector))
            return

        try {
            config.getConnection().use { connection ->
                connection.prepareStatement("INSERT INTO Inventory " + "(hash, stream, expires, data, type, version) VALUES (?, ?, ?, ?, ?, ?)").use { ps ->
                    val iv = objectMessage.inventoryVector
                    LOG.trace("Storing object " + iv)
                    ps.setBytes(1, iv.hash)
                    ps.setLong(2, objectMessage.stream)
                    ps.setLong(3, objectMessage.expiresTime)
                    JdbcHelper.Companion.writeBlob(ps, 4, objectMessage)
                    ps.setLong(5, objectMessage.type)
                    ps.setLong(6, objectMessage.version)
                    ps.executeUpdate()
                    getCache(objectMessage.stream).put(iv, objectMessage.expiresTime)
                }
            }
        } catch (e: SQLException) {
            LOG.debug("Error storing object of type " + objectMessage.payload.javaClass.simpleName, e)
        } catch (e: Exception) {
            LOG.error(e.message, e)
        }
    }

    override fun contains(objectMessage: ObjectMessage): Boolean {
        return getCache(objectMessage.stream).any { (key, _) -> key == objectMessage.inventoryVector }
    }

    override fun cleanup() {
        try {
            config.getConnection().use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.executeUpdate("DELETE FROM Inventory WHERE expires < " + (now - 5 * MINUTE))
                }
            }
        } catch (e: SQLException) {
            LOG.debug(e.message, e)
        }

        for (c in cache.values) {
            c.entries.removeIf { e -> e.value < now - 5 * MINUTE }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcInventory::class.java)
    }
}
