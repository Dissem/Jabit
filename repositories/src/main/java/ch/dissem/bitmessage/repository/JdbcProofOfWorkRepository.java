package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository;
import ch.dissem.bitmessage.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * @author Christian Basler
 */
public class JdbcProofOfWorkRepository extends JdbcHelper implements ProofOfWorkRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcProofOfWorkRepository.class);

    public JdbcProofOfWorkRepository(JdbcConfig config) {
        super(config);
    }

    @Override
    public ObjectMessage getObject(byte[] initialHash) {
        try (Connection connection = config.getConnection()) {
            PreparedStatement ps = connection.prepareStatement("SELECT data, version FROM POW WHERE initial_hash=?");
            ps.setBytes(1, initialHash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Blob data = rs.getBlob("data");
                return Factory.getObjectMessage(rs.getInt("version"), data.getBinaryStream(), (int) data.length());
            } else {
                throw new RuntimeException("Object requested that we don't have. Initial hash: " + Strings.hex(initialHash));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void putObject(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) {
        try (Connection connection = config.getConnection()) {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO POW (initial_hash, data, version) VALUES (?, ?, ?)");
            ps.setBytes(1, security().getInitialHash(object));
            writeBlob(ps, 2, object);
            ps.setLong(3, object.getVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.debug("Error storing object of type " + object.getPayload().getClass().getSimpleName(), e);
            throw new RuntimeException(e);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeObject(ObjectMessage object) {
        try (Connection connection = config.getConnection()) {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM POW WHERE initial_hash=?");
            ps.setBytes(1, security().getInitialHash(object));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.debug(e.getMessage(), e);
        }
    }
}
