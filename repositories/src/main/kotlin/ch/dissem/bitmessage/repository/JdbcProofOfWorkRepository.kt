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

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.ProofOfWorkRepository
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.Strings
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * @author Christian Basler
 */
class JdbcProofOfWorkRepository(config: JdbcConfig) : JdbcHelper(config), ProofOfWorkRepository, InternalContext.ContextHolder {
    private lateinit var ctx: InternalContext

    override fun getItem(initialHash: ByteArray): ProofOfWorkRepository.Item {
        config.getConnection().use { connection ->
            connection.prepareStatement("""
                SELECT data, version, nonce_trials_per_byte, extra_bytes, expiration_time, message_id
                FROM POW
                WHERE initial_hash=?
            """).use { ps ->
                ps.setBytes(1, initialHash)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val data = rs.getBlob("data")
                        if (rs.getObject("message_id") == null) {
                            return ProofOfWorkRepository.Item(
                                Factory.getObjectMessage(rs.getInt("version"), data.binaryStream, data.length().toInt())!!,
                                rs.getLong("nonce_trials_per_byte"),
                                rs.getLong("extra_bytes")
                            )
                        } else {
                            return ProofOfWorkRepository.Item(
                                Factory.getObjectMessage(rs.getInt("version"), data.binaryStream, data.length().toInt())!!,
                                rs.getLong("nonce_trials_per_byte"),
                                rs.getLong("extra_bytes"),
                                rs.getLong("expiration_time"),
                                ctx.messageRepository.getMessage(rs.getLong("message_id"))
                            )
                        }
                    } else {
                        throw IllegalArgumentException("Object requested that we don't have. Initial hash: " + Strings.hex(initialHash))
                    }
                }
            }
        }
    }

    override fun getItems(): List<ByteArray> {
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT initial_hash FROM POW").use { rs ->
                    val result = mutableListOf<ByteArray>()
                    while (rs.next()) {
                        result.add(rs.getBytes("initial_hash"))
                    }
                    return result
                }
            }
        }
    }

    override fun putObject(item: ProofOfWorkRepository.Item) {
        config.getConnection().use { connection ->
            connection.prepareStatement("""
                INSERT INTO
                   POW (initial_hash, data, version, nonce_trials_per_byte, extra_bytes, expiration_time, message_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)""").use { ps ->
                ps.setBytes(1, cryptography().getInitialHash(item.objectMessage))
                JdbcHelper.Companion.writeBlob(ps, 2, item.objectMessage)
                ps.setLong(3, item.objectMessage.version)
                ps.setLong(4, item.nonceTrialsPerByte)
                ps.setLong(5, item.extraBytes)

                if (item.message == null) {
                    ps.setObject(6, null)
                    ps.setObject(7, null)
                } else {
                    ps.setLong(6, item.expirationTime!!)
                    ps.setLong(7, item.message!!.id as Long)
                }
                ps.executeUpdate()
            }
        }
    }

    override fun putObject(objectMessage: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long) {
        putObject(ProofOfWorkRepository.Item(objectMessage, nonceTrialsPerByte, extraBytes))
    }

    override fun removeObject(initialHash: ByteArray) {
        try {
            config.getConnection().use { connection ->
                connection.prepareStatement("DELETE FROM POW WHERE initial_hash=?").use { ps ->
                    ps.setBytes(1, initialHash)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            LOG.debug(e.message, e)
        }
    }

    override fun setContext(context: InternalContext) {
        ctx = context
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcProofOfWorkRepository::class.java)
    }
}
