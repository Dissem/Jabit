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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static ch.dissem.bitmessage.utils.Collections.selectRandom;
import static ch.dissem.bitmessage.utils.UnixTime.HOUR;
import static java.util.Collections.newSetFromMap;

public class MemoryNodeRegistry implements NodeRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(MemoryNodeRegistry.class);

    private final Map<Long, Set<NetworkAddress>> stableNodes = new ConcurrentHashMap<>();
    private final Map<Long, Set<NetworkAddress>> knownNodes = new ConcurrentHashMap<>();

    private void loadStableNodes() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("nodes.txt")) {
            Scanner scanner = new Scanner(in);
            long stream = 0;
            Set<NetworkAddress> streamSet = null;
            while (scanner.hasNext()) {
                try {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        // Ignore
                        continue;
                    }
                    if (line.startsWith("[stream")) {
                        stream = Long.parseLong(line.substring(8, line.lastIndexOf(']')));
                        streamSet = new HashSet<>();
                        stableNodes.put(stream, streamSet);
                    } else if (streamSet != null) {
                        int portIndex = line.lastIndexOf(':');
                        InetAddress[] inetAddresses = InetAddress.getAllByName(line.substring(0, portIndex));
                        int port = Integer.valueOf(line.substring(portIndex + 1));
                        for (InetAddress inetAddress : inetAddresses) {
                            streamSet.add(new NetworkAddress.Builder().ip(inetAddress).port(port).stream(stream).build());
                        }
                    }
                } catch (IOException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
            if (LOG.isDebugEnabled()) {
                for (Map.Entry<Long, Set<NetworkAddress>> e : stableNodes.entrySet()) {
                    LOG.debug("Stream " + e.getKey() + ": loaded " + e.getValue().size() + " bootstrap nodes.");
                }
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    @Override
    public List<NetworkAddress> getKnownAddresses(int limit, long... streams) {
        List<NetworkAddress> result = new LinkedList<>();
        for (long stream : streams) {
            Set<NetworkAddress> known = knownNodes.get(stream);
            if (known != null && !known.isEmpty()) {
                for (NetworkAddress node : known) {
                    if (node.getTime() > UnixTime.now(-3 * HOUR)) {
                        result.add(node);
                    } else {
                        known.remove(node);
                    }
                }
            } else {
                Set<NetworkAddress> nodes = stableNodes.get(stream);
                if (nodes == null || nodes.isEmpty()) {
                    loadStableNodes();
                    nodes = stableNodes.get(stream);
                }
                if (nodes != null && !nodes.isEmpty()) {
                    // To reduce load on stable nodes, only return one
                    result.add(selectRandom(nodes));
                }
            }
        }
        return selectRandom(limit, result);
    }

    @Override
    public void offerAddresses(List<NetworkAddress> addresses) {
        for (NetworkAddress node : addresses) {
            if (node.getTime() <= UnixTime.now()) {
                if (!knownNodes.containsKey(node.getStream())) {
                    synchronized (knownNodes) {
                        if (!knownNodes.containsKey(node.getStream())) {
                            knownNodes.put(
                                    node.getStream(),
                                    newSetFromMap(new ConcurrentHashMap<NetworkAddress, Boolean>())
                            );
                        }
                    }
                }
                if (node.getTime() <= UnixTime.now()) {
                    // TODO: This isn't quite correct
                    // If the node is already known, the one with the more recent time should be used
                    knownNodes.get(node.getStream()).add(node);
                }
            }
        }
    }
}
