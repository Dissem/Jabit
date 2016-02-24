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

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.MessageRepository;
import ch.dissem.bitmessage.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class JdbcMessageRepository extends JdbcHelper implements MessageRepository, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcMessageRepository.class);

    private InternalContext ctx;

    public JdbcMessageRepository(JdbcConfig config) {
        super(config);
    }

    @Override
    public List<Label> getLabels() {
        List<Label> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, label, type, color FROM Label ORDER BY ord");
            while (rs.next()) {
                result.add(getLabel(rs));
            }
        } catch (SQLException e) {
            throw new ApplicationException(e);
        }
        return result;
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
    public List<Label> getLabels(Label.Type... types) {
        List<Label> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, label, type, color FROM Label WHERE type IN (" + join(types) +
                    ") ORDER BY ord");
            while (rs.next()) {
                result.add(getLabel(rs));
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public int countUnread(Label label) {
        String where;
        if (label != null) {
            where = "id IN (SELECT message_id FROM Message_Label WHERE label_id=" + label.getId() + ") AND ";
        } else {
            where = "";
        }
        where += "id IN (SELECT message_id FROM Message_Label WHERE label_id IN (" +
                "SELECT id FROM Label WHERE type = '" + Label.Type.UNREAD.name() + "'))";

        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM Message WHERE " + where);
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public Plaintext getMessage(byte[] initialHash) {
        List<Plaintext> plaintexts = find("initial_hash=X'" + Strings.hex(initialHash) + "'");
        switch (plaintexts.size()) {
            case 0:
                return null;
            case 1:
                return plaintexts.get(0);
            default:
                throw new ApplicationException("This shouldn't happen, found " + plaintexts.size() +
                        " messages, one or none was expected");
        }
    }

    @Override
    public List<Plaintext> findMessages(Label label) {
        return find("id IN (SELECT message_id FROM Message_Label WHERE label_id=" + label.getId() + ")");
    }

    @Override
    public List<Plaintext> findMessages(Plaintext.Status status, BitmessageAddress recipient) {
        return find("status='" + status.name() + "' AND recipient='" + recipient.getAddress() + "'");
    }

    @Override
    public List<Plaintext> findMessages(Plaintext.Status status) {
        return find("status='" + status.name() + "'");
    }

    @Override
    public List<Plaintext> findMessages(BitmessageAddress sender) {
        return find("sender='" + sender.getAddress() + "'");
    }

    private List<Plaintext> find(String where) {
        List<Plaintext> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, iv, type, sender, recipient, data, sent, received, status FROM Message WHERE " + where);
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
                builder.sent(rs.getLong("sent"));
                builder.received(rs.getLong("received"));
                builder.status(Plaintext.Status.valueOf(rs.getString("status")));
                builder.labels(findLabels(connection, id));
                result.add(builder.build());
            }
        } catch (IOException | SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        return result;
    }

    private Collection<Label> findLabels(Connection connection, long messageId) {
        List<Label> result = new ArrayList<>();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, label, type, color FROM Label WHERE id IN (SELECT label_id FROM Message_Label WHERE message_id=" + messageId + ")");
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
        // save from address if necessary
        if (message.getId() == null) {
            BitmessageAddress savedAddress = ctx.getAddressRepository().getAddress(message.getFrom().getAddress());
            if (savedAddress == null || savedAddress.getPrivateKey() == null) {
                if (savedAddress != null && savedAddress.getAlias() != null) {
                    message.getFrom().setAlias(savedAddress.getAlias());
                }
                ctx.getAddressRepository().save(message.getFrom());
            }
        }

        try (Connection connection = config.getConnection()) {
            try {
                connection.setAutoCommit(false);
                // save message
                if (message.getId() == null) {
                    insert(connection, message);
                } else {
                    update(connection, message);
                }

                // remove existing labels
                Statement stmt = connection.createStatement();
                stmt.executeUpdate("DELETE FROM Message_Label WHERE message_id=" + message.getId());

                // save labels
                PreparedStatement ps = connection.prepareStatement("INSERT INTO Message_Label VALUES (" + message.getId() + ", ?)");
                for (Label label : message.getLabels()) {
                    ps.setLong(1, (Long) label.getId());
                    ps.executeUpdate();
                }

                connection.commit();
            } catch (IOException | SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    LOG.debug(e1.getMessage(), e);
                }
                throw new ApplicationException(e);
            }
        } catch (SQLException e) {
            throw new ApplicationException(e);
        }
    }

    private void insert(Connection connection, Plaintext message) throws SQLException, IOException {
        PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Message (iv, type, sender, recipient, data, sent, received, status, initial_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
        ps.setBytes(1, message.getInventoryVector() != null ? message.getInventoryVector().getHash() : null);
        ps.setString(2, message.getType().name());
        ps.setString(3, message.getFrom().getAddress());
        ps.setString(4, message.getTo() != null ? message.getTo().getAddress() : null);
        writeBlob(ps, 5, message);
        ps.setLong(6, message.getSent());
        ps.setLong(7, message.getReceived());
        ps.setString(8, message.getStatus() != null ? message.getStatus().name() : null);
        ps.setBytes(9, message.getInitialHash());

        ps.executeUpdate();

        // get generated id
        ResultSet rs = ps.getGeneratedKeys();
        rs.next();
        message.setId(rs.getLong(1));
    }

    private void update(Connection connection, Plaintext message) throws SQLException, IOException {
        PreparedStatement ps = connection.prepareStatement(
                "UPDATE Message SET iv=?, sent=?, received=?, status=?, initial_hash=? WHERE id=?");
        ps.setBytes(1, message.getInventoryVector() != null ? message.getInventoryVector().getHash() : null);
        ps.setLong(2, message.getSent());
        ps.setLong(3, message.getReceived());
        ps.setString(4, message.getStatus() != null ? message.getStatus().name() : null);
        ps.setBytes(5, message.getInitialHash());
        ps.setLong(6, (Long) message.getId());
        ps.executeUpdate();
    }

    @Override
    public void remove(Plaintext message) {
        try (Connection connection = config.getConnection()) {
            try {
                connection.setAutoCommit(false);
                Statement stmt = connection.createStatement();
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

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }
}
