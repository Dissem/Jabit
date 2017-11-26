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

package ch.dissem.bitmessage.repository

import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.ports.AbstractLabelRepository
import ch.dissem.bitmessage.ports.LabelRepository
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

class JdbcLabelRepository(private val config: JdbcConfig) : AbstractLabelRepository(), LabelRepository {

    override fun find(where: String): List<Label> {
        try {
            config.getConnection().use { connection ->
                return findLabels(connection, where)
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
            return ArrayList()
        }
    }

    override fun save(label: Label) {
        config.getConnection().use { connection ->
            if (label.id != null) {
                connection.prepareStatement("UPDATE Label SET label=?, type=?, color=?, ord=? WHERE id=?").use { ps ->
                    ps.setString(1, label.toString())
                    ps.setString(2, label.type?.name)
                    ps.setInt(3, label.color)
                    ps.setInt(4, label.ord)
                    ps.setInt(5, label.id as Int)
                    ps.executeUpdate()
                }
            } else {
                try {
                    connection.autoCommit = false
                    var exists = false
                    connection.prepareStatement("SELECT COUNT(1) FROM Label WHERE label=?").use { ps ->
                        ps.setString(1, label.toString())
                        val rs = ps.executeQuery()
                        if (rs.next()) {
                            exists = rs.getInt(1) > 0
                        }
                    }

                    if (exists) {
                        connection.prepareStatement("UPDATE Label SET type=?, color=?, ord=? WHERE label=?").use { ps ->
                            ps.setString(1, label.type?.name)
                            ps.setInt(2, label.color)
                            ps.setInt(3, label.ord)
                            ps.setString(4, label.toString())
                            ps.executeUpdate()
                        }
                    } else {
                        connection.prepareStatement("INSERT INTO Label (label, type, color, ord) VALUES (?, ?, ?, ?)").use { ps ->
                            ps.setString(1, label.toString())
                            ps.setString(2, label.type?.name)
                            ps.setInt(3, label.color)
                            ps.setInt(4, label.ord)
                            ps.executeUpdate()
                        }
                    }
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        }
    }

    private fun findLabels(connection: Connection, where: String): List<Label> {
        val result = ArrayList<Label>()
        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT id, label, type, color, ord FROM Label WHERE $where").use { rs ->
                    while (rs.next()) {
                        result.add(getLabel(rs))
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcLabelRepository::class.java)

        internal fun getLabel(rs: ResultSet): Label {
            val typeName = rs.getString("type")
            val type = if (typeName == null) {
                null
            } else {
                Label.Type.valueOf(typeName)
            }
            val label = Label(rs.getString("label"), type, rs.getInt("color"), rs.getInt("ord"))
            label.id = rs.getLong("id")

            return label
        }
    }
}
