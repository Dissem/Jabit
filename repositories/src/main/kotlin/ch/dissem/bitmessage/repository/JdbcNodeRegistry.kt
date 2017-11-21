/*
 * Copyright 2016 Christian Basler
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

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.ports.NodeRegistry
import ch.dissem.bitmessage.ports.NodeRegistryHelper.loadStableNodes
import ch.dissem.bitmessage.utils.*
import ch.dissem.bitmessage.utils.Collections
import ch.dissem.bitmessage.utils.UnixTime.DAY
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import ch.dissem.bitmessage.utils.UnixTime.now
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

class JdbcNodeRegistry(config: JdbcConfig) : JdbcHelper(config), NodeRegistry {
    private var stableNodes: Map<Long, Set<NetworkAddress>> = emptyMap()
        get() {
            if (field.isEmpty())
                field = loadStableNodes()
            return field
        }

    init {
        cleanUp()
    }

    private fun cleanUp() {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement("DELETE FROM Node WHERE time<?").use { ps ->
                    ps.setLong(1, now - 28 * DAY)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    private fun loadExisting(node: NetworkAddress): NetworkAddress? {
        val query = """
            SELECT stream, address, port, services, time
            FROM Node
            WHERE stream = ${node.stream} AND address = X'${Strings.hex(node.IPv6)}' AND port = ${node.port}
        """
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(query).use { rs ->
                    if (rs.next()) {
                        return NetworkAddress.Builder()
                            .stream(rs.getLong("stream"))
                            .ipv6(rs.getBytes("address"))
                            .port(rs.getInt("port"))
                            .services(rs.getLong("services"))
                            .time(rs.getLong("time"))
                            .build()
                    } else {
                        return null
                    }
                }
            }
        }
    }

    override fun clear() {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement("DELETE FROM Node").use { ps -> ps.executeUpdate() }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    override fun getKnownAddresses(limit: Int, vararg streams: Long): List<NetworkAddress> {
        val result = LinkedList<NetworkAddress>()
        val query = """
            SELECT stream, address, port, services, time
            FROM Node
            WHERE stream IN (${SqlStrings.join(*streams)})
            ORDER BY TIME DESC LIMIT $limit
        """
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(query).use { rs ->
                    while (rs.next()) {
                        result.add(
                            NetworkAddress.Builder()
                                .stream(rs.getLong("stream"))
                                .ipv6(rs.getBytes("address"))
                                .port(rs.getInt("port"))
                                .services(rs.getLong("services"))
                                .time(rs.getLong("time"))
                                .build()
                        )
                    }
                }
            }
        }

        if (result.isEmpty()) {
            streams
                .asSequence()
                .mapNotNull { stableNodes[it] }
                .filter { it.isNotEmpty() }
                .mapTo(result) { Collections.selectRandom(it) }
            if (result.isEmpty()) {
                // There might have been an error resolving domain names due to a missing internet connection.
                // Try to load the stable nodes again next time.
                stableNodes = emptyMap()
            }
        }
        return result
    }

    override fun offerAddresses(nodes: List<NetworkAddress>) {
        cleanUp()
        nodes.stream()
            .filter { (time) -> time < now + 2 * MINUTE && time > now - 28 * DAY }
            .forEach { node ->
                synchronized(this) {
                    val existing = loadExisting(node)
                    if (existing == null) {
                        insert(node)
                    } else if (node.time > existing.time) {
                        update(node)
                    }
                }
            }
    }

    private fun insert(node: NetworkAddress) {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement(
                    "INSERT INTO Node (stream, address, port, services, time) VALUES (?, ?, ?, ?, ?)").use { ps ->
                    ps.setLong(1, node.stream)
                    ps.setBytes(2, node.IPv6)
                    ps.setInt(3, node.port)
                    ps.setLong(4, node.services)
                    ps.setLong(5,
                        if (node.time > UnixTime.now) {
                            // This might be an attack, let's not use those nodes with priority
                            UnixTime.now - 7 * UnixTime.DAY
                        } else if (node.time == 0L) {
                            // Those just don't have a time set
                            // let's give them slightly higher priority than the possible attack ones
                            UnixTime.now - 6 * UnixTime.DAY
                        } else {
                            node.time
                        }
                    )
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    override fun update(node: NetworkAddress) {
        try {
            val time = if (node.time > UnixTime.now) {
                // This might be an attack, let's not use those nodes with priority
                UnixTime.now - 7 * UnixTime.DAY
            } else {
                node.time
            }

            config.getConnection().use { connection ->
                connection.prepareStatement(
                    "UPDATE Node SET services=?, time=? WHERE stream=? AND address=? AND port=?").use { ps ->
                    ps.setLong(1, node.services)
                    ps.setLong(2, max(node.time, time))
                    ps.setLong(3, node.stream)
                    ps.setBytes(4, node.IPv6)
                    ps.setInt(5, node.port)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    override fun remove(node: NetworkAddress) {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM Node WHERE stream=? AND address=? AND port=?").use { ps ->
                    ps.setLong(1, node.stream)
                    ps.setBytes(2, node.IPv6)
                    ps.setInt(3, node.port)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    override fun cleanup() {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM Node WHERE time<?").use { ps ->
                    ps.setLong(1, UnixTime.now - 8 * DAY)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcNodeRegistry::class.java)
    }
}
