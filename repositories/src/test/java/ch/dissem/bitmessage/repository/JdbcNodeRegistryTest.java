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
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static ch.dissem.bitmessage.utils.UnixTime.now;
import static org.junit.Assert.assertEquals;

public class JdbcNodeRegistryTest {
    private TestJdbcConfig config;
    private NodeRegistry registry;

    @Before
    public void setUp() throws Exception {
        config = new TestJdbcConfig();
        config.reset();
        registry = new JdbcNodeRegistry(config);

        registry.offerAddresses(Arrays.asList(
                createAddress(1, 8444, 1, now()),
                createAddress(2, 8444, 1, now()),
                createAddress(3, 8444, 1, now()),
                createAddress(4, 8444, 2, now())
        ));
    }

    @Test
    public void testGetKnownAddresses() throws Exception {
        List<NetworkAddress> knownAddresses = registry.getKnownAddresses(2, 1);
        assertEquals(2, knownAddresses.size());

        knownAddresses = registry.getKnownAddresses(1000, 1);
        assertEquals(3, knownAddresses.size());
    }

    @Test
    public void testOfferAddresses() throws Exception {
        registry.offerAddresses(Arrays.asList(
                createAddress(1, 8444, 1, now()),
                createAddress(10, 8444, 1, now()),
                createAddress(11, 8444, 1, now())
        ));

        List<NetworkAddress> knownAddresses = registry.getKnownAddresses(1000, 1);
        assertEquals(5, knownAddresses.size());

        registry.offerAddresses(Arrays.asList(
                createAddress(1, 8445, 1, now())
        ));

        knownAddresses = registry.getKnownAddresses(1000, 1);
        assertEquals(6, knownAddresses.size());
    }

    private NetworkAddress createAddress(int lastByte, int port, long stream, long time) {
        return new NetworkAddress.Builder()
                .ipv6(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, lastByte)
                .port(port)
                .stream(stream)
                .time(time)
                .build();
    }
}