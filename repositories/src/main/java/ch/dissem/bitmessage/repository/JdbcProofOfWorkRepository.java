package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository;
import ch.dissem.bitmessage.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * @author Christian Basler
 */
public class JdbcProofOfWorkRepository extends JdbcHelper implements ProofOfWorkRepository, InternalContext.ContextHolder {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcProofOfWorkRepository.class);
    private InternalContext ctx;

    public JdbcProofOfWorkRepository(JdbcConfig config) {
        super(config);
    }

    @Override
    public Item getItem(byte[] initialHash) {
        try (
                Connection connection = config.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT data, version, nonce_trials_per_byte, " +
                        "extra_bytes, expiration_time, message_id FROM POW WHERE initial_hash=?")
        ) {
            ps.setBytes(1, initialHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Blob data = rs.getBlob("data");
                    if (rs.getObject("message_id") == null) {
                        return new Item(
                                Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length()),
                                rs.getLong("nonce_trials_per_byte"),
                                rs.getLong("extra_bytes")
                        );
                    } else {
                        return new Item(
                                Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length()),
                                rs.getLong("nonce_trials_per_byte"),
                                rs.getLong("extra_bytes"),
                                rs.getLong("expiration_time"),
                                ctx.getMessageRepository().getMessage(rs.getLong("message_id"))
                        );
                    }
                } else {
                    throw new IllegalArgumentException("Object requested that we don't have. Initial hash: " + Strings.hex(initialHash));
                }
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
            throw new ApplicationException(e);
        }
    }

    @Override
    public List<byte[]> getItems() {
        try (
                Connection connection = config.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT initial_hash FROM POW")
        ) {
            List<byte[]> result = new LinkedList<>();
            while (rs.next()) {
                result.add(rs.getBytes("initial_hash"));
            }
            return result;
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
            throw new ApplicationException(e);
        }
    }

    @Override
    public void putObject(Item item) {
        try (
                Connection connection = config.getConnection();
                PreparedStatement ps = connection.prepareStatement("INSERT INTO POW (initial_hash, data, version, " +
                        "nonce_trials_per_byte, extra_bytes, expiration_time, message_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")
        ) {
            ps.setBytes(1, cryptography().getInitialHash(item.object));
            writeBlob(ps, 2, item.object);
            ps.setLong(3, item.object.getVersion());
            ps.setLong(4, item.nonceTrialsPerByte);
            ps.setLong(5, item.extraBytes);

            if (item.message == null) {
                ps.setObject(6, null);
                ps.setObject(7, null);
            } else {
                ps.setLong(6, item.expirationTime);
                ps.setLong(7, (Long) item.message.getId());
            }
            ps.executeUpdate();
        } catch (IOException | SQLException e) {
            LOG.debug("Error storing object of type " + item.object.getPayload().getClass().getSimpleName(), e);
            throw new ApplicationException(e);
        }
    }

    @Override
    public void putObject(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) {
        putObject(new Item(object, nonceTrialsPerByte, extraBytes));
    }

    @Override
    public void removeObject(byte[] initialHash) {
        try (
                Connection connection = config.getConnection();
                PreparedStatement ps = connection.prepareStatement("DELETE FROM POW WHERE initial_hash=?")
        ) {
            ps.setBytes(1, initialHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.debug(e.getMessage(), e);
        }
    }

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }
}
