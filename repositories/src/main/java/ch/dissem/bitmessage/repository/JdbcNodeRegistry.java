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

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.ports.NodeRegistry;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import static ch.dissem.bitmessage.utils.UnixTime.HOUR;

public class JdbcNodeRegistry extends JdbcHelper implements NodeRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcNodeRegistry.class);

    public JdbcNodeRegistry(JdbcConfig config) {
        super(config);
    }

    @Override
    public List<NetworkAddress> getKnownAddresses(int limit, long... streams) {
        List<NetworkAddress> result = new LinkedList<>();
        try (Connection connection = config.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM Node WHERE stream IN (" + join(streams) + ") ORDER BY RANDOM() LIMIT " + limit);
            while (rs.next()) {
                result.add(new NetworkAddress.Builder()
                        .ipv6(rs.getBytes("ip"))
                        .port(rs.getInt("port"))
                        .services(rs.getLong("services"))
                        .stream(rs.getLong("stream"))
                        .time(rs.getLong("time"))
                        .build());
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
        if (result.isEmpty()) {
            // FIXME: this is for testing purposes, remove it!
            result.add(new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build());
        }
        return result;
    }

    @Override
    public void offerAddresses(List<NetworkAddress> addresses) {
        try (Connection connection = config.getConnection()) {
            PreparedStatement exists = connection.prepareStatement("SELECT time FROM Node WHERE ip = ? AND port = ? AND stream = ?");
            PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO Node (ip, port, services, stream, time) VALUES (?, ?, ?, ?, ?)");
            PreparedStatement update = connection.prepareStatement(
                    "UPDATE Node SET services = ?, time = ? WHERE ip = ? AND port = ? AND stream = ?");
            connection.setAutoCommit(false);
            for (NetworkAddress node : addresses) {
                exists.setBytes(1, node.getIPv6());
                exists.setInt(2, node.getPort());
                exists.setLong(3, node.getStream());
                ResultSet lastConnectionTime = exists.executeQuery();
                if (lastConnectionTime.next()) {
                    long time = lastConnectionTime.getLong("time");
                    if (time < node.getTime() && node.getTime() < UnixTime.now()) {
                        time = node.getTime();
                        update.setLong(1, node.getServices());
                        update.setLong(2, time);

                        update.setBytes(3, node.getIPv6());
                        update.setInt(4, node.getPort());
                        update.setLong(5, node.getStream());
                        update.executeUpdate();
                    }
                } else if (node.getTime() < UnixTime.now()) {
                    insert.setBytes(1, node.getIPv6());
                    insert.setInt(2, node.getPort());
                    insert.setLong(3, node.getServices());
                    insert.setLong(4, node.getStream());
                    insert.setLong(5, node.getTime());
                    insert.executeUpdate();
                }
                connection.commit();
            }
            if (addresses.size() > 100) {
                // Let's clean up after we received an update from another node. This way, we shouldn't end up with an
                // empty node registry.
                PreparedStatement cleanup = connection.prepareStatement("DELETE FROM Node WHERE time < ?");
                cleanup.setLong(1, UnixTime.now(-3 * HOUR));
                cleanup.execute();
            }
        } catch (SQLException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
