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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by chris on 23.04.15.
 */
public class JdbcAddressRepository extends DatabaseRepository implements AddressRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseRepository.class);

    @Override
    public List<BitmessageAddress> findIdentities() {
        return find("private_signing_key IS NOT NULL");
    }

    @Override
    public List<BitmessageAddress> findContacts() {
        return find("private_signing_key IS NULL");
    }

    private List<BitmessageAddress> find(String where) {
        List<BitmessageAddress> result = new LinkedList<>();
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT address, alias, public_key, private_key FROM Address WHERE " + where);
            while (rs.next()) {
                BitmessageAddress address;
                Blob privateKeyBlob = rs.getBlob("private_key");
                if (privateKeyBlob != null) {
                    PrivateKey privateKey = PrivateKey.read(privateKeyBlob.getBinaryStream());
                    address = new BitmessageAddress(privateKey);
                } else {
                    address = new BitmessageAddress(rs.getString("address"));
                    Blob publicKeyBlob = rs.getBlob("public_key");
                    if (publicKeyBlob != null) {
                        Pubkey pubkey = Factory.readPubkey(address.getVersion(), address.getStream(),
                                publicKeyBlob.getBinaryStream(), (int)publicKeyBlob.length());
                        address.setPubkey(pubkey);
                    }
                }
                address.setAlias(rs.getString("alias"));

                result.add(address);
            }
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public void save(BitmessageAddress address) {
        try {
            PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO Address (address, alias, public_key, private_key) VALUES (?, ?, ?, ?, ?)");
            ps.setString(1, address.getAddress());
            ps.setString(2, address.getAlias());
            writeBlob(ps, 3, address.getPubkey());
            writeBlob(ps, 4, address.getPrivateKey());
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public void remove(BitmessageAddress address) {
        try {
            Statement stmt = getConnection().createStatement();
            stmt.executeUpdate("DELETE FROM Address WHERE address = '" + address.getAddress() + "'");
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
