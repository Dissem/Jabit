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

package ch.dissem.bitmessage.inventory;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.Inventory;
import ch.dissem.bitmessage.ports.NodeRegistry;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Strings.join;
import static ch.dissem.bitmessage.utils.UnixTime.now;

/**
 * Stores everything in a database
 */
public class DatabaseRepository implements Inventory, NodeRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseRepository.class);

    private static final String DB_URL = "jdbc:h2:~/jabit";
    private static final String DB_USER = "sa";
    private static final String DB_PWD = null;


    public DatabaseRepository() {
        Flyway flyway = new Flyway();
        flyway.setDataSource(DB_URL, DB_USER, null);
        flyway.migrate();
    }

    @Override
    public List<NetworkAddress> getKnownAddresses(int limit, long... streams) {
        List<NetworkAddress> result = new LinkedList<>();
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM Node WHERE Stream IN (" + join(streams) + ")");
            while (rs.next()) {
//                result.add(new NetworkAddress.Builder()
//                        .ipv6(rs.getBytes("ip"))
//                        .port(rs.getByte("port"))
//                        .services(rs.getLong("services"))
//                        .stream(rs.getLong("stream"))
//                        .time(rs.getLong("time"))
//                        .build());
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        if (result.isEmpty()) {
            // FIXME: this is for testing purposes, remove it!
            result.add(new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build());
        }
        return result;
    }

    @Override
    public void offerAddresses(List<NetworkAddress> addresses) {
        try {
            Connection connection = getConnection();
            PreparedStatement exists = connection.prepareStatement("SELECT port FROM Node WHERE ip = ? AND port = ? AND stream = ?");
            PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO Node (ip, port, services, stream, time) VALUES (?, ?, ?, ?, ?)");
            PreparedStatement update = connection.prepareStatement(
                    "UPDATE Node SET services = ?, time = ? WHERE ip = ? AND port = ? AND stream = ?");
            for (NetworkAddress node : addresses) {
                exists.setBytes(1, node.getIPv6());
                exists.setInt(2, node.getPort());
                exists.setLong(3, node.getStream());
                if (exists.executeQuery().next()) {
                    update.setLong(1, node.getServices());
                    update.setLong(2, node.getTime());

                    update.setBytes(3, node.getIPv6());
                    update.setInt(4, node.getPort());
                    update.setLong(5, node.getStream());
                    update.executeUpdate();
                } else {
                    insert.setBytes(1, node.getIPv6());
                    insert.setInt(2, node.getPort());
                    insert.setLong(3, node.getServices());
                    insert.setLong(4, node.getStream());
                    insert.setLong(5, node.getTime());
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public List<InventoryVector> getInventory(long... streams) {
        List<InventoryVector> result = new LinkedList<>();
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT hash FROM Inventory WHERE expires > " + now() +
                    " AND stream IN (" + join(streams) + ")");
            while (rs.next()) {
                result.add(new InventoryVector(rs.getBytes("hash")));
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<InventoryVector> getMissing(List<InventoryVector> offer, long... streams) {
        offer.removeAll(getInventory(streams));
        return offer;
    }

    @Override
    public ObjectMessage getObject(InventoryVector vector) {
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT data, version FROM Inventory WHERE hash = " + vector);
            Blob data = rs.getBlob("data");
            return Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length());
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void storeObject(int version, ObjectMessage object) {
        try {
            PreparedStatement ps = getConnection().prepareStatement("INSERT INTO Inventory (hash, stream, expires, data, type, version) VALUES (?, ?, ?, ?, ?, ?)");
            InventoryVector iv = object.getInventoryVector();
            LOG.trace("Storing object " + iv);
            ps.setBytes(1, iv.getHash());
            ps.setLong(2, object.getStream());
            ps.setLong(3, object.getExpiresTime());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            object.write(os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            ps.setBlob(4, is);
            ps.setLong(5, object.getType());
            ps.setInt(6, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error storing object of type " + object.getPayload().getClass().getSimpleName(), e);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    protected void writeBlob(PreparedStatement ps, int parameterIndex, Streamable data) throws SQLException, IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        data.write(os);
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        ps.setBlob(parameterIndex, is);
    }

    @Override
    public void cleanup() {
        try {
            // We delete only objects that expired 5 minutes ago or earlier, so we don't request objects we just deleted
            getConnection().createStatement().executeUpdate("DELETE FROM Inventory WHERE expires < " + (now() - 300));
        } catch (SQLException e) {
            LOG.debug(e.getMessage(), e);
        }
    }

    protected Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
