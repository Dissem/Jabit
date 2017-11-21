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
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.payload.V3Pubkey
import ch.dissem.bitmessage.entity.payload.V4Pubkey
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.AddressRepository
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.*

class JdbcAddressRepository(config: JdbcConfig) : JdbcHelper(config), AddressRepository {

    override fun findContact(ripeOrTag: ByteArray) = find("private_key is null").firstOrNull {
        if (it.version > 3) {
            Arrays.equals(ripeOrTag, it.tag)
        } else {
            Arrays.equals(ripeOrTag, it.ripe)
        }
    }

    override fun findIdentity(ripeOrTag: ByteArray) = find("private_key is not null").firstOrNull {
        if (it.version > 3) {
            Arrays.equals(ripeOrTag, it.tag)
        } else {
            Arrays.equals(ripeOrTag, it.ripe)
        }
    }

    override fun getIdentities() = find("private_key IS NOT NULL")

    override fun getChans() = find("chan = '1'")

    override fun getSubscriptions() = find("subscribed = '1'")

    override fun getSubscriptions(broadcastVersion: Long): List<BitmessageAddress> = if (broadcastVersion > 4) {
        find("subscribed = '1' AND version > 3")
    } else {
        find("subscribed = '1' AND version <= 3")
    }

    override fun getContacts() = find("private_key IS NULL OR chan = '1'")

    private fun find(where: String): List<BitmessageAddress> {
        val result = LinkedList<BitmessageAddress>()
        try {
            config.getConnection().use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.executeQuery("""
                        SELECT address, alias, public_key, private_key, subscribed, chan
                        FROM Address
                        WHERE $where
                    """).use { rs ->
                        while (rs.next()) {
                            val address: BitmessageAddress

                            val privateKeyStream = rs.getBinaryStream("private_key")
                            if (privateKeyStream == null) {
                                address = BitmessageAddress(rs.getString("address"))
                                rs.getBlob("public_key")?.let { publicKeyBlob ->
                                    var pubkey: Pubkey = Factory.readPubkey(address.version, address.stream,
                                        publicKeyBlob.binaryStream, publicKeyBlob.length().toInt(), false)!!
                                    if (address.version == 4L && pubkey is V3Pubkey) {
                                        pubkey = V4Pubkey(pubkey)
                                    }
                                    address.pubkey = pubkey
                                }
                            } else {
                                val privateKey = PrivateKey.read(privateKeyStream)
                                address = BitmessageAddress(privateKey)
                            }
                            address.alias = rs.getString("alias")
                            address.isSubscribed = rs.getBoolean("subscribed")
                            address.isChan = rs.getBoolean("chan")

                            result.add(address)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

        return result
    }

    private fun exists(address: BitmessageAddress): Boolean {
        config.getConnection().use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT '1' FROM Address " +
                    "WHERE address='" + address.address + "'").use { rs -> return rs.next() }
            }
        }
    }

    override fun save(address: BitmessageAddress) {
        try {
            if (exists(address)) {
                update(address)
            } else {
                insert(address)
            }
        } catch (e: IOException) {
            LOG.error(e.message, e)
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }
    }

    private fun update(address: BitmessageAddress) {
        val statement = StringBuilder("UPDATE Address SET alias=?")
        if (address.pubkey != null) {
            statement.append(", public_key=?")
        }
        if (address.privateKey != null) {
            statement.append(", private_key=?")
        }
        statement.append(", subscribed=?, chan=? WHERE address=?")
        config.getConnection().use { connection ->
            connection.prepareStatement(statement.toString()).use { ps ->
                var i = 0
                ps.setString(++i, address.alias)
                if (address.pubkey != null) {
                    writePubkey(ps, ++i, address.pubkey)
                }
                if (address.privateKey != null) {
                    JdbcHelper.writeBlob(ps, ++i, address.privateKey)
                }
                ps.setBoolean(++i, address.isSubscribed)
                ps.setBoolean(++i, address.isChan)
                ps.setString(++i, address.address)
                ps.executeUpdate()
            }
        }
    }

    private fun insert(address: BitmessageAddress) {
        config.getConnection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO Address (address, version, alias, public_key, private_key, subscribed, chan) " + "VALUES (?, ?, ?, ?, ?, ?, ?)").use { ps ->
                ps.setString(1, address.address)
                ps.setLong(2, address.version)
                ps.setString(3, address.alias)
                writePubkey(ps, 4, address.pubkey)
                JdbcHelper.writeBlob(ps, 5, address.privateKey)
                ps.setBoolean(6, address.isSubscribed)
                ps.setBoolean(7, address.isChan)
                ps.executeUpdate()
            }
        }
    }

    private fun writePubkey(ps: PreparedStatement, parameterIndex: Int, data: Pubkey?) {
        if (data != null) {
            val out = ByteArrayOutputStream()
            data.writer().writeUnencrypted(out)
            ps.setBytes(parameterIndex, out.toByteArray())
        } else {
            ps.setBytes(parameterIndex, null)
        }
    }

    override fun remove(address: BitmessageAddress) {
        try {
            config.getConnection().use { connection -> connection.createStatement().use { stmt -> stmt.executeUpdate("DELETE FROM Address WHERE address = '" + address.address + "'") } }
        } catch (e: SQLException) {
            LOG.error(e.message, e)
        }

    }

    override fun getAddress(address: String): BitmessageAddress? {
        val result = find("address = '$address'")
        if (result.isNotEmpty()) return result[0]
        return null
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcAddressRepository::class.java)
    }
}
