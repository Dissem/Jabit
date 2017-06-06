/*
 * Copyright 2016 Christian Basler
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

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.ports.NodeRegistry;
import ch.dissem.bitmessage.utils.Collections;
import ch.dissem.bitmessage.utils.SqlStrings;
import ch.dissem.bitmessage.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.dissem.bitmessage.ports.NodeRegistryHelper.loadStableNodes;
import static ch.dissem.bitmessage.utils.UnixTime.*;

public class JdbcNodeRegistry extends JdbcHelper implements NodeRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcNodeRegistry.class);
    private Map<Long, Set<NetworkAddress>> stableNodes;

    public JdbcNodeRegistry(JdbcConfig config) {
        super(config);
        cleanUp();
    }

    private void cleanUp() {
        try (
            Connection connection = config.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM Node WHERE time<?")
        ) {
            ps.setLong(1, now() - 28 * DAY);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private NetworkAddress loadExisting(NetworkAddress node) {
        String query =
            "SELECT stream, address, port, services, time" +
                " FROM Node" +
                " WHERE stream = " + node.getStream() +
                "   AND address = X'" + Strings.hex(node.getIPv6()) + "'" +
                "   AND port = " + node.getPort();
        try (
            Connection connection = config.getConnection();
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(query)
        ) {
            if (rs.next()) {
                return new NetworkAddress.Builder()
                    .stream(rs.getLong("stream"))
                    .ipv6(rs.getBytes("address"))
                    .port(rs.getInt("port"))
                    .services(rs.getLong("services"))
                    .time(rs.getLong("time"))
                    .build();
            } else {
                return null;
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new ApplicationException(e);
        }
    }

    @Override
    public void clear() {
        try (
            Connection connection = config.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM Node")
        ) {
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public List<NetworkAddress> getKnownAddresses(int limit, long... streams) {
        List<NetworkAddress> result = new LinkedList<>();
        String query =
            "SELECT stream, address, port, services, time" +
                " FROM Node WHERE stream IN (" + SqlStrings.join(streams) + ")" +
                " ORDER BY TIME DESC" +
                " LIMIT " + limit;
        try (
            Connection connection = config.getConnection();
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(query)
        ) {
            while (rs.next()) {
                result.add(
                    new NetworkAddress.Builder()
                        .stream(rs.getLong("stream"))
                        .ipv6(rs.getBytes("address"))
                        .port(rs.getInt("port"))
                        .services(rs.getLong("services"))
                        .time(rs.getLong("time"))
                        .build()
                );
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new ApplicationException(e);
        }
        if (result.isEmpty()) {
            synchronized (this) {
                if (stableNodes == null) {
                    stableNodes = loadStableNodes();
                }
            }
            for (long stream : streams) {
                Set<NetworkAddress> nodes = stableNodes.get(stream);
                if (nodes != null && !nodes.isEmpty()) {
                    result.add(Collections.selectRandom(nodes));
                }
            }
            if (result.isEmpty()) {
                // There might have been an error resolving domain names due to a missing internet exception.
                // Try to load the stable nodes again next time.
                stableNodes = null;
            }
        }
        return result;
    }

    @Override
    public void offerAddresses(List<NetworkAddress> nodes) {
        cleanUp();
        nodes.stream()
            .filter(node -> node.getTime() < now() + 2 * MINUTE && node.getTime() > now() - 28 * DAY)
            .forEach(node -> {
                synchronized (this) {
                    NetworkAddress existing = loadExisting(node);
                    if (existing == null) {
                        insert(node);
                    } else if (node.getTime() > existing.getTime()) {
                        update(node);
                    }
                }
            });
    }

    private void insert(NetworkAddress node) {
        try (
            Connection connection = config.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Node (stream, address, port, services, time) " +
                    "VALUES (?, ?, ?, ?, ?)")
        ) {
            ps.setLong(1, node.getStream());
            ps.setBytes(2, node.getIPv6());
            ps.setInt(3, node.getPort());
            ps.setLong(4, node.getServices());
            ps.setLong(5, node.getTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void update(NetworkAddress node) {
        try (
            Connection connection = config.getConnection();
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE Node SET services=?, time=? WHERE stream=? AND address=? AND port=?")
        ) {
            ps.setLong(1, node.getServices());
            ps.setLong(2, node.getTime());
            ps.setLong(3, node.getStream());
            ps.setBytes(4, node.getIPv6());
            ps.setInt(5, node.getPort());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
