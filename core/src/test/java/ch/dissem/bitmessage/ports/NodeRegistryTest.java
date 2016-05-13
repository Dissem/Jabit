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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.utils.UnixTime;
import org.junit.Test;

import java.util.Arrays;

import static ch.dissem.bitmessage.utils.UnixTime.HOUR;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class NodeRegistryTest {
    private NodeRegistry registry = new MemoryNodeRegistry();

    @Test
    public void ensureGetKnownNodesWithoutStreamsYieldsEmpty() {
        assertThat(registry.getKnownAddresses(10), empty());
    }

    @Test
    public void ensureGetKnownNodesForStream1YieldsResult() {
        assertThat(registry.getKnownAddresses(10, 1), hasSize(1));
    }

    @Test
    public void ensureNodeIsStored() {
        registry.offerAddresses(Arrays.asList(
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 1)
                        .port(42)
                        .stream(1)
                        .time(UnixTime.now())
                        .build(),
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 2)
                        .port(42)
                        .stream(1)
                        .time(UnixTime.now())
                        .build(),
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 2)
                        .port(42)
                        .stream(2)
                        .time(UnixTime.now())
                        .build()
        ));
        assertThat(registry.getKnownAddresses(10, 1).size(), is(2));
        assertThat(registry.getKnownAddresses(10, 2).size(), is(1));
        assertThat(registry.getKnownAddresses(10, 1, 2).size(), is(3));
    }

    @Test
    public void ensureOldNodesAreRemoved() {
        registry.offerAddresses(Arrays.asList(
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 1)
                        .port(42)
                        .stream(1)
                        .time(UnixTime.now())
                        .build(),
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 2)
                        .port(42)
                        .stream(1)
                        .time(UnixTime.now(-4 * HOUR))
                        .build(),
                new NetworkAddress.Builder()
                        .ipv4(127, 0, 0, 2)
                        .port(42)
                        .stream(2)
                        .time(UnixTime.now())
                        .build()
        ));
        assertThat(registry.getKnownAddresses(10, 1).size(), is(1));
        assertThat(registry.getKnownAddresses(10, 2).size(), is(1));
        assertThat(registry.getKnownAddresses(10, 1, 2).size(), is(2));
    }
}
