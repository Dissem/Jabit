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

package ch.dissem.bitmessage.inventory;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.ports.NodeRegistry;

import java.util.Collections;
import java.util.List;

/**
 * Created by chris on 06.04.15.
 */
public class SimpleNodeRegistry implements NodeRegistry {
    @Override
    public List<NetworkAddress> getKnownAddresses(int limit, long... streams) {
        return Collections.singletonList(new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build());
    }

    @Override
    public void offerAddresses(List<NetworkAddress> addresses) {
    }
}
