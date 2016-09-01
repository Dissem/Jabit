package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;

/**
 * Helper class to kick start node registries.
 */
public class NodeRegistryHelper {
    private static final Logger LOG = LoggerFactory.getLogger(NodeRegistryHelper.class);

    public static Map<Long, Set<NetworkAddress>> loadStableNodes() {
        try (InputStream in = NodeRegistryHelper.class.getClassLoader().getResourceAsStream("nodes.txt")) {
            Scanner scanner = new Scanner(in);
            long stream = 0;
            Map<Long, Set<NetworkAddress>> result = new HashMap<>();
            Set<NetworkAddress> streamSet = null;
            while (scanner.hasNext()) {
                try {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("[stream")) {
                        stream = Long.parseLong(line.substring(8, line.lastIndexOf(']')));
                        streamSet = new HashSet<>();
                        result.put(stream, streamSet);
                    } else if (streamSet != null && !line.isEmpty() && !line.startsWith("#")) {
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
                for (Map.Entry<Long, Set<NetworkAddress>> e : result.entrySet()) {
                    LOG.debug("Stream " + e.getKey() + ": loaded " + e.getValue().size() + " bootstrap nodes.");
                }
            }
            return result;
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }
}
