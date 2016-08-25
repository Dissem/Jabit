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

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.AbstractMessageRepository;
import ch.dissem.bitmessage.ports.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.repository.JdbcHelper.writeBlob;

public class JdbcMessageRepository extends AbstractMessageRepository implements MessageRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcMessageRepository.class);

    private final JdbcConfig config;

    public JdbcMessageRepository(JdbcConfig config) {
        this.config = config;
    }

    @Override
    protected List<Label> findLabels(String where) {
        try (
                Connection connection = config.getConnection()
        ) {
            return findLabels(connection, where);
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private Label getLabel(ResultSet rs) throws SQLException {
        String typeName = rs.getString("type");
        Label.Type type = null;
        if (typeName != null) {
            type = Label.Type.valueOf(typeName);
        }
        Label label = new Label(rs.getString("label"), type, rs.getInt("color"));
        label.setId(rs.getLong("id"));

        return label;
    }

    @Override
    public int countUnread(Label label) {
        String where;
        if (label == null) {
            where = "";
        } else {
            where = "id IN (SELECT message_id FROM Message_Label WHERE label_id=" + label.getId() + ") AND ";
        }
        where += "id IN (SELECT message_id FROM Message_Label WHERE label_id IN (" +
                "SELECT id FROM Label WHERE type = '" + Label.Type.UNREAD.name() + "'))";

        try (
                Connection connection = config.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM Message WHERE " + where)
        ) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    protected List<Plaintext> find(String where) {
        List<Plaintext> result = new LinkedList<>();
        try (
                Connection connection = config.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, iv, type, sender, recipient, data, ack_data, sent, received, initial_hash, status, ttl, retries, next_try " +
                                "FROM Message WHERE " + where)
        ) {
            while (rs.next()) {
                byte[] iv = rs.getBytes("iv");
                InputStream data = rs.getBinaryStream("data");
                Plaintext.Type type = Plaintext.Type.valueOf(rs.getString("type"));
                Plaintext.Builder builder = Plaintext.readWithoutSignature(type, data);
                long id = rs.getLong("id");
                builder.id(id);
                builder.IV(new InventoryVector(iv));
                builder.from(ctx.getAddressRepository().getAddress(rs.getString("sender")));
                builder.to(ctx.getAddressRepository().getAddress(rs.getString("recipient")));
                builder.ackData(rs.getBytes("ack_data"));
                builder.sent(rs.getLong("sent"));
                builder.received(rs.getLong("received"));
                builder.status(Plaintext.Status.valueOf(rs.getString("status")));
                builder.ttl(rs.getLong("ttl"));
                builder.retries(rs.getInt("retries"));
                builder.nextTry(rs.getLong("next_try"));
                builder.labels(findLabels(connection,
                        "id IN (SELECT label_id FROM Message_Label WHERE message_id=" + id + ") ORDER BY ord"));
                Plaintext message = builder.build();
                message.setInitialHash(rs.getBytes("initial_hash"));
                result.add(message);
            }
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    private List<Label> findLabels(Connection connection, String where) {
        List<Label> result = new ArrayList<>();
        try (
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, label, type, color FROM Label WHERE " + where)
        ) {
            while (rs.next()) {
                result.add(getLabel(rs));
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public void save(Plaintext message) {
        safeSenderIfNecessary(message);

        try (Connection connection = config.getConnection()) {
            try {
                connection.setAutoCommit(false);
                save(connection, message);
                updateLabels(connection, message);
                connection.commit();
            } catch (IOException | SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (IOException | SQLException e) {
            throw new ApplicationException(e);
        }
    }

    private void save(Connection connection, Plaintext message) throws IOException, SQLException {
        if (message.getId() == null) {
            insert(connection, message);
        } else {
            update(connection, message);
        }
    }

    private void updateLabels(Connection connection, Plaintext message) throws SQLException {
        // remove existing labels
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM Message_Label WHERE message_id=" + message.getId());
        }
        // save new labels
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO Message_Label VALUES (" +
                message.getId() + ", ?)")) {
            for (Label label : message.getLabels()) {
                ps.setLong(1, (Long) label.getId());
                ps.executeUpdate();
            }
        }
    }

    private void insert(Connection connection, Plaintext message) throws SQLException, IOException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Message (iv, type, sender, recipient, data, ack_data, sent, received, " +
                        "status, initial_hash, ttl, retries, next_try) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)
        ) {
            ps.setBytes(1, message.getInventoryVector() == null ? null : message.getInventoryVector().getHash());
            ps.setString(2, message.getType().name());
            ps.setString(3, message.getFrom().getAddress());
            ps.setString(4, message.getTo() == null ? null : message.getTo().getAddress());
            writeBlob(ps, 5, message);
            ps.setBytes(6, message.getAckData());
            ps.setLong(7, message.getSent());
            ps.setLong(8, message.getReceived());
            ps.setString(9, message.getStatus() == null ? null : message.getStatus().name());
            ps.setBytes(10, message.getInitialHash());
            ps.setLong(11, message.getTTL());
            ps.setInt(12, message.getRetries());
            ps.setObject(13, message.getNextTry());

            ps.executeUpdate();
            // get generated id
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                message.setId(rs.getLong(1));
            }
        }
    }

    private void update(Connection connection, Plaintext message) throws SQLException, IOException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Message SET iv=?, type=?, sender=?, recipient=?, data=?, ack_data=?, sent=?, received=?, " +
                        "status=?, initial_hash=?, ttl=?, retries=?, next_try=? " +
                        "WHERE id=?")) {
            ps.setBytes(1, message.getInventoryVector() == null ? null : message.getInventoryVector().getHash());
            ps.setString(2, message.getType().name());
            ps.setString(3, message.getFrom().getAddress());
            ps.setString(4, message.getTo() == null ? null : message.getTo().getAddress());
            writeBlob(ps, 5, message);
            ps.setBytes(6, message.getAckData());
            ps.setLong(7, message.getSent());
            ps.setLong(8, message.getReceived());
            ps.setString(9, message.getStatus() == null ? null : message.getStatus().name());
            ps.setBytes(10, message.getInitialHash());
            ps.setLong(11, message.getTTL());
            ps.setInt(12, message.getRetries());
            ps.setObject(13, message.getNextTry());
            ps.setLong(14, (Long) message.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void remove(Plaintext message) {
        try (Connection connection = config.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM Message_Label WHERE message_id = " + message.getId());
                stmt.executeUpdate("DELETE FROM Message WHERE id = " + message.getId());
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    LOG.debug(e1.getMessage(), e);
                }
                LOG.error(e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
