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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The 'addr' command holds a list of known active Bitmessage nodes.
 */
public class Addr implements MessagePayload {
    private final List<NetworkAddress> addresses;

    private Addr(Builder builder) {
        addresses = builder.addresses;
    }

    @Override
    public Command getCommand() {
        return Command.ADDR;
    }

    public List<NetworkAddress> getAddresses() {
        return addresses;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        Encode.varInt(addresses.size(), stream);
        for (NetworkAddress address : addresses) {
            address.write(stream);
        }
    }

    public static final class Builder {
        private List<NetworkAddress> addresses = new ArrayList<NetworkAddress>();

        public Builder addresses(Collection<NetworkAddress> addresses){
            this.addresses.addAll(addresses);
            return this;
        }

        public Builder addAddress(final NetworkAddress address) {
            this.addresses.add(address);
            return this;
        }

        public Addr build() {
            return new Addr(this);
        }
    }
}
