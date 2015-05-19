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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by chris on 23.04.15.
 */
public class JdbcAddressRepository extends JdbcHelper implements AddressRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcAddressRepository.class);

    @Override
    public BitmessageAddress findContact(byte[] ripeOrTag) {
        for (BitmessageAddress address : find("public_key is null")) {
            if (address.getVersion() > 3) {
                if (Arrays.equals(ripeOrTag, address.getTag())) return address;
            } else {
                if (Arrays.equals(ripeOrTag, address.getRipe())) return address;
            }
        }
        return null;
    }

    @Override
    public BitmessageAddress findIdentity(byte[] ripeOrTag) {
        for (BitmessageAddress address : find("private_key is not null")) {
            if (address.getVersion() > 3) {
                if (Arrays.equals(ripeOrTag, address.getTag())) return address;
            } else {
                if (Arrays.equals(ripeOrTag, address.getRipe())) return address;
            }
        }
        return null;
    }

    @Override
    public List<BitmessageAddress> getIdentities() {
        return find("private_signing_key IS NOT NULL");
    }

    @Override
    public List<BitmessageAddress> getSubscriptions() {
        return find("subscribed = '1'");
    }

    @Override
    public List<BitmessageAddress> getContacts() {
        return find("private_signing_key IS NULL");
    }

    private List<BitmessageAddress> find(String where) {
        List<BitmessageAddress> result = new LinkedList<>();
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT address, alias, public_key, private_key, subscribed FROM Address WHERE " + where);
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
                                publicKeyBlob.getBinaryStream(), (int) publicKeyBlob.length());
                        address.setPubkey(pubkey);
                    }
                }
                address.setAlias(rs.getString("alias"));
                address.setSubscribed(rs.getBoolean("subscribed"));

                result.add(address);
            }
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    private boolean exists(BitmessageAddress address) {
        try {
            Statement stmt = getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Address WHERE address='" + address.getAddress() + "'");
            rs.next();
            return rs.getInt(0) > 0;
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void save(BitmessageAddress address) {
        try {
            if (exists(address)) {
                update(address);
            } else {
                insert(address);
            }
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void update(BitmessageAddress address) throws IOException, SQLException {
        PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE Address SET address=?, alias=?, public_key=?, private_key=?");
        ps.setString(1, address.getAddress());
        ps.setString(2, address.getAlias());
        writeBlob(ps, 3, address.getPubkey());
        writeBlob(ps, 4, address.getPrivateKey());
        ps.executeUpdate();
    }

    private void insert(BitmessageAddress address) throws IOException, SQLException {
        PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO Address (address, alias, public_key, private_key) VALUES (?, ?, ?, ?, ?)");
        ps.setString(1, address.getAddress());
        ps.setString(2, address.getAlias());
        writeBlob(ps, 3, address.getPubkey());
        writeBlob(ps, 4, address.getPrivateKey());
        ps.executeUpdate();
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

    @Override
    public BitmessageAddress getAddress(String address) {
        List<BitmessageAddress> result = find("address = '" + address + "'");
        if (result.size() > 0) return result.get(0);
        return null;
    }
}
