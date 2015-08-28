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

package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.UnixTime.now;

public class JdbcInventory extends JdbcHelper implements Inventory {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcInventory.class);

    public JdbcInventory(JdbcConfig config) {
        super(config);
    }

    @Override
    public List<InventoryVector> getInventory(long... streams) {
        List<InventoryVector> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
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

    private List<InventoryVector> getFullInventory(long... streams) {
        List<InventoryVector> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT hash FROM Inventory WHERE stream IN (" + join(streams) + ")");
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
        offer.removeAll(getFullInventory(streams));
        return offer;
    }

    @Override
    public ObjectMessage getObject(InventoryVector vector) {
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT data, version FROM Inventory WHERE hash = X'" + vector + "'");
            if (rs.next()) {
                Blob data = rs.getBlob("data");
                return Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length());
            } else {
                LOG.info("Object requested that we don't have. IV: " + vector);
                return null;
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ObjectMessage> getObjects(long stream, long version, ObjectType... types) {
        try (Connection connection = config.getConnection()) {
            StringBuilder query = new StringBuilder("SELECT data, version FROM Inventory WHERE 1=1");
            if (stream > 0) {
                query.append(" AND stream = ").append(stream);
            }
            if (version > 0) {
                query.append(" AND version = ").append(version);
            }
            if (types.length > 0) {
                query.append(" AND type IN (").append(join(types)).append(")");
            }
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(query.toString());
            List<ObjectMessage> result = new LinkedList<>();
            while (rs.next()) {
                Blob data = rs.getBlob("data");
                result.add(Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length()));
            }
            return result;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void storeObject(ObjectMessage object) {
        try (Connection connection = config.getConnection()) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO Inventory (hash, stream, expires, data, type, version) VALUES (?, ?, ?, ?, ?, ?)");
            InventoryVector iv = object.getInventoryVector();
            LOG.trace("Storing object " + iv);
            ps.setBytes(1, iv.getHash());
            ps.setLong(2, object.getStream());
            ps.setLong(3, object.getExpiresTime());
            writeBlob(ps, 4, object);
            ps.setLong(5, object.getType());
            ps.setLong(6, object.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.debug("Error storing object of type " + object.getPayload().getClass().getSimpleName(), e);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public void cleanup() {
        try (Connection connection = config.getConnection()) {
            // We delete only objects that expired 5 minutes ago or earlier, so we don't request objects we just deleted
            connection.createStatement().executeUpdate("DELETE FROM Inventory WHERE expires < " + (now() - 300));
        } catch (SQLException e) {
            LOG.debug(e.getMessage(), e);
        }
    }
}
