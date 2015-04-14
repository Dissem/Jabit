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
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.AddressRepository;
import ch.dissem.bitmessage.ports.Inventory;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Strings.join;

/**
 * Stores everything in a database
 */
public class DatabaseRepository implements Inventory, AddressRepository {
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
            PreparedStatement exists = connection.prepareStatement("SELECT port FROM Node WHERE ip = ? AND port = ?");
            PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO Node (ip, port, services, stream, time) VALUES (?, ?, ?, ?, ?)");
            PreparedStatement update = connection.prepareStatement(
                    "UPDATE Node SET services = ?, stream = ?, time = ? WHERE ip = ? AND port = ?");
            for (NetworkAddress node : addresses) {
                exists.setBytes(1, node.getIPv6());
                exists.setInt(2, node.getPort());
                if (exists.executeQuery().next()) {
                    update.setLong(1, node.getServices());
                    update.setLong(2, node.getStream());
                    update.setLong(3, node.getTime());

                    update.setBytes(4, node.getIPv6());
                    update.setInt(5, node.getPort());
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
            ResultSet rs = stmt.executeQuery("SELECT hash FROM Inventory WHERE Stream IN (" + join(streams) + ")");
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
            PreparedStatement ps = getConnection().prepareStatement("INSERT INTO Inventory (hash, stream, expires, data, version) VALUES (?, ?, ?, ?, ?)");
            InventoryVector iv = object.getInventoryVector();
            LOG.error("Storing object " + iv);
            ps.setBytes(1, iv.getHash());
            ps.setLong(2, object.getStream());
            ps.setLong(3, object.getExpiresTime());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            object.write(os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            ps.setBlob(4, is);
            ps.setInt(5, version);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public void cleanup() {
        try {
            getConnection().createStatement().executeUpdate("DELETE FROM Inventory WHERE time < " + (System.currentTimeMillis() / 1000));
        } catch (SQLException e) {
            LOG.debug(e.getMessage(), e);
        }
    }

    private Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
