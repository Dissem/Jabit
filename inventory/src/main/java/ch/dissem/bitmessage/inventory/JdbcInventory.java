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
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Strings.join;
import static ch.dissem.bitmessage.utils.UnixTime.now;

/**
 * Created by chris on 24.04.15.
 */
public class JdbcInventory extends JdbcHelper implements Inventory {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcInventory.class);

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
            writeBlob(ps, 4, object);
            ps.setLong(5, object.getType());
            ps.setInt(6, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error storing object of type " + object.getPayload().getClass().getSimpleName(), e);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
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
}
