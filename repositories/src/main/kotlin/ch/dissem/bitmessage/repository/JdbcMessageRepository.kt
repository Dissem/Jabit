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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.ports.AbstractMessageRepository
import ch.dissem.bitmessage.ports.AlreadyStoredException
import ch.dissem.bitmessage.ports.MessageRepository
import ch.dissem.bitmessage.repository.JdbcHelper.Companion.writeBlob
import org.slf4j.LoggerFactory
import java.sql.*
import java.util.*

class JdbcMessageRepository(private val config: JdbcConfig) : AbstractMessageRepository(), MessageRepository {

    override fun countUnread(label: Label?): Int {
        val where = if (label == null) {
            ""
        } else {
            "id IN (SELECT message_id FROM Message_Label WHERE label_id=${label.id}) AND "
        } + "id IN (SELECT message_id FROM Message_Label WHERE label_id IN (" +
            "SELECT id FROM Label WHERE type = '" + Label.Type.UNREAD.name + "'))"

        try {
            config.getConnection().use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT count(1) FROM Message WHERE $where").use { rs ->
                        if (rs.next()) {
                            return rs.getInt(1)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
        return 0
    }

    override fun find(where: String, offset: Int, limit: Int): List<Plaintext> {
        val result = LinkedList<Plaintext>()
        val limit = if (limit == 0) "" else "LIMIT $limit OFFSET $offset"
        try {
            config.getConnection().use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(
                        """SELECT id, iv, type, sender, recipient, data, ack_data, sent, received, initial_hash, status, ttl, retries, next_try, conversation
                           FROM Message WHERE $where $limit""").use { rs ->
                        while (rs.next()) {
                            val message = getMessage(connection, rs)
                            message.initialHash = rs.getBytes("initial_hash")
                            result.add(message)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
        return result
    }

    private fun getMessage(connection: Connection, rs: ResultSet): Plaintext {
        return Plaintext.readWithoutSignature(
            Plaintext.Type.valueOf(rs.getString("type")),
            rs.getBinaryStream("data")
        ).build {
            id = rs.getLong("id")
            inventoryVector = InventoryVector.fromHash(rs.getBytes("iv"))
            from = rs.getString("sender")?.let { ctx.addressRepository.getAddress(it) ?: BitmessageAddress(it) }
            to = rs.getString("recipient")?.let { ctx.addressRepository.getAddress(it) ?: BitmessageAddress(it) }
            ackData = rs.getBytes("ack_data")
            sent = rs.getObject("sent") as Long?
            received = rs.getObject("received") as Long?
            status = Plaintext.Status.valueOf(rs.getString("status"))
            ttl = rs.getLong("ttl")
            retries = rs.getInt("retries")
            nextTry = rs.getObject("next_try") as Long?
            conversation = rs.getObject("conversation") as UUID? ?: UUID.randomUUID()
            labels = findLabels(connection,
                "id IN (SELECT label_id FROM Message_Label WHERE message_id=$id) ORDER BY ord")
        }
    }

    private fun findLabels(connection: Connection, where: String): List<Label> {
        val result = ArrayList<Label>()
        try {
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT id, label, type, color, ord FROM Label WHERE $where").use { rs ->
                    while (rs.next()) {
                        result.add(JdbcLabelRepository.getLabel(rs))
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

        return result
    }

    override fun save(message: Plaintext) {
        saveContactIfNecessary(message.from)
        saveContactIfNecessary(message.to)

        config.getConnection().use { connection ->
            try {
                connection.autoCommit = false
                save(connection, message)
                updateParents(connection, message)
                updateLabels(connection, message)
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }

    private fun save(connection: Connection, message: Plaintext) {
        if (message.id == null) {
            insert(connection, message)
        } else {
            update(connection, message)
        }
    }

    private fun updateLabels(connection: Connection, message: Plaintext) {
        // remove existing labels
        connection.createStatement().use { stmt -> stmt.executeUpdate("DELETE FROM Message_Label WHERE message_id=${message.id!!}") }
        // save new labels
        connection.prepareStatement("INSERT INTO Message_Label VALUES (${message.id}, ?)").use { ps ->
            for (label in message.labels) {
                ps.setLong(1, (label.id as Long?)!!)
                ps.executeUpdate()
            }
        }
    }

    private fun updateParents(connection: Connection, message: Plaintext) {
        val childIV = message.inventoryVector?.hash
        if (childIV == null || message.parents.isEmpty()) {
            // There are no parents to save yet (they are saved in the extended data, that's enough for now)
            return
        }
        // remove existing parents
        connection.prepareStatement("DELETE FROM Message_Parent WHERE child=?").use { ps ->
            ps.setBytes(1, message.initialHash)
            ps.executeUpdate()
        }
        // save new parents
        var order = 0
        connection.prepareStatement("INSERT INTO Message_Parent VALUES (?, ?, ?, ?)").use { ps ->
            for (parentIV in message.parents) {
                getMessage(parentIV)?.let { parent ->
                    mergeConversations(connection, parent.conversationId, message.conversationId)
                    order++
                    ps.setBytes(1, parentIV.hash)
                    ps.setBytes(2, childIV)
                    ps.setInt(3, order) // FIXME: this might not be necessary
                    ps.setObject(4, message.conversationId)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun insert(connection: Connection, message: Plaintext) {
        connection.prepareStatement(
            "INSERT INTO Message (iv, type, sender, recipient, data, ack_data, sent, received, " +
                "status, initial_hash, ttl, retries, next_try, conversation) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS).use { ps ->
            prepare(ps, message)
            ps.setObject(14, message.conversationId)

            try {
                ps.executeUpdate()
                // get generated id
                ps.generatedKeys.use { rs ->
                    rs.next()
                    message.id = rs.getLong(1)
                }
            } catch (e: SQLIntegrityConstraintViolationException) {
                throw AlreadyStoredException(cause = e)
            }
        }
    }

    private fun update(connection: Connection, message: Plaintext) {
        connection.prepareStatement(
            "UPDATE Message SET iv=?, type=?, sender=?, recipient=?, data=?, ack_data=?, sent=?, received=?, " +
                "status=?, initial_hash=?, ttl=?, retries=?, next_try=? " +
                "WHERE id=?").use { ps ->
            prepare(ps, message)
            ps.setLong(14, (message.id as Long?)!!)
            ps.executeUpdate()
        }
    }

    private fun prepare(ps: PreparedStatement, message: Plaintext): Int{
        ps.setBytes(1, if (message.inventoryVector == null) null else message.inventoryVector!!.hash)
        ps.setString(2, message.type.name)
        ps.setString(3, message.from.address)
        ps.setString(4, if (message.to == null) null else message.to!!.address)
        writeBlob(ps, 5, message)
        ps.setBytes(6, message.ackData)
        ps.setObject(7, message.sent)
        ps.setObject(8, message.received)
        ps.setString(9, message.status.name)
        ps.setBytes(10, message.initialHash)
        ps.setLong(11, message.ttl)
        ps.setInt(12, message.retries)
        ps.setObject(13, message.nextTry)
        return 14
    }

    override fun remove(message: Plaintext) {
        try {
            config.getConnection().use { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { stmt ->
                        stmt.executeUpdate("DELETE FROM Message_Label WHERE message_id = " + message.id!!)
                        stmt.executeUpdate("DELETE FROM Message WHERE id = " + message.id!!)
                        connection.commit()
                    }
                } catch (e: SQLException) {
                    try {
                        connection.rollback()
                    } catch (e1: SQLException) {
                        LOG.debug(e1.message, e)
                    }

                    LOG.error(e.message, e)
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    override fun findConversations(label: Label?): List<UUID> {
        val where = if (label == null) {
            "id NOT IN (SELECT message_id FROM Message_Label)"
        } else {
            "id IN (SELECT message_id FROM Message_Label WHERE label_id=${label.id})"
        }
        val result = LinkedList<UUID>()
        try {
            config.getConnection().use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(
                        "SELECT DISTINCT conversation FROM Message WHERE $where").use { rs ->
                        while (rs.next()) {
                            result.add(rs.getObject(1) as UUID)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

        return result
    }

    /**
     * Replaces every occurrence of the source conversation ID with the target ID

     * @param source ID of the conversation to be merged
     * *
     * @param target ID of the merge target
     */
    private fun mergeConversations(connection: Connection, source: UUID, target: UUID) {
        try {
            connection.prepareStatement(
                "UPDATE Message SET conversation=? WHERE conversation=?").use { ps1 ->
                connection.prepareStatement(
                    "UPDATE Message_Parent SET conversation=? WHERE conversation=?").use { ps2 ->
                    ps1.setObject(1, target)
                    ps1.setObject(2, source)
                    ps1.executeUpdate()
                    ps2.setObject(1, target)
                    ps2.setObject(2, source)
                    ps2.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcMessageRepository::class.java)
    }
}
