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
import ch.dissem.bitmessage.entity.payload.V3Pubkey;
import ch.dissem.bitmessage.entity.payload.V4Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class JdbcAddressRepository extends JdbcHelper implements AddressRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcAddressRepository.class);

    public JdbcAddressRepository(JdbcConfig config) {
        super(config);
    }

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
        return find("private_key IS NOT NULL");
    }

    @Override
    public List<BitmessageAddress> getSubscriptions() {
        return find("subscribed = '1'");
    }

    @Override
    public List<BitmessageAddress> getSubscriptions(long broadcastVersion) {
        if (broadcastVersion > 4) {
            return find("subscribed = '1' AND version > 3");
        } else {
            return find("subscribed = '1' AND version <= 3");
        }
    }

    @Override
    public List<BitmessageAddress> getContacts() {
        return find("private_key IS NULL");
    }

    private List<BitmessageAddress> find(String where) {
        List<BitmessageAddress> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT address, alias, public_key, private_key, subscribed FROM Address WHERE " + where);
            while (rs.next()) {
                BitmessageAddress address;

                InputStream privateKeyStream = rs.getBinaryStream("private_key");
                if (privateKeyStream != null) {
                    PrivateKey privateKey = PrivateKey.read(privateKeyStream);
                    address = new BitmessageAddress(privateKey);
                } else {
                    address = new BitmessageAddress(rs.getString("address"));
                    Blob publicKeyBlob = rs.getBlob("public_key");
                    if (publicKeyBlob != null) {
                        Pubkey pubkey = Factory.readPubkey(address.getVersion(), address.getStream(),
                                publicKeyBlob.getBinaryStream(), (int) publicKeyBlob.length(), false);
                        if (address.getVersion() == 4 && pubkey instanceof V3Pubkey) {
                            pubkey = new V4Pubkey((V3Pubkey) pubkey);
                        }
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
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Address WHERE address='" + address.getAddress() + "'");
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
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
        try (Connection connection = config.getConnection()) {
            StringBuilder statement = new StringBuilder("UPDATE Address SET alias=?");
            if (address.getPubkey() != null) {
                statement.append(", public_key=?");
            }
            if (address.getPrivateKey() != null) {
                statement.append(", private_key=?");
            }
            statement.append(", subscribed=? WHERE address=?");
            PreparedStatement ps = connection.prepareStatement(statement.toString());
            int i = 0;
            ps.setString(++i, address.getAlias());
            if (address.getPubkey() != null) {
                writePubkey(ps, ++i, address.getPubkey());
            }
            if (address.getPrivateKey() != null) {
                writeBlob(ps, ++i, address.getPrivateKey());
            }
            ps.setBoolean(++i, address.isSubscribed());
            ps.setString(++i, address.getAddress());
            ps.executeUpdate();
        }
    }

    private void insert(BitmessageAddress address) throws IOException, SQLException {
        try (Connection connection = config.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO Address (address, version, alias, public_key, private_key, subscribed) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setString(1, address.getAddress());
            ps.setLong(2, address.getVersion());
            ps.setString(3, address.getAlias());
            writePubkey(ps, 4, address.getPubkey());
            writeBlob(ps, 5, address.getPrivateKey());
            ps.setBoolean(6, address.isSubscribed());
            ps.executeUpdate();
        }
    }

    protected void writePubkey(PreparedStatement ps, int parameterIndex, Pubkey data) throws SQLException, IOException {
        if (data != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.writeUnencrypted(out);
            ps.setBytes(parameterIndex, out.toByteArray());
        } else {
            ps.setBytes(parameterIndex, null);
        }
    }

    @Override
    public void remove(BitmessageAddress address) {
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
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
