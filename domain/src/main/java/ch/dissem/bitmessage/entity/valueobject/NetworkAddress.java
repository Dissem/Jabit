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

package ch.dissem.bitmessage.entity.valueobject;

import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * A node's address. It's written in IPv6 format.
 */
public class NetworkAddress implements Streamable {
    private long time;

    /**
     * Stream number for this node
     */
    private long stream;

    /**
     * same service(s) listed in version
     */
    private long services;

    /**
     * IPv6 address. IPv4 addresses are written into the message as a 16 byte IPv4-mapped IPv6 address
     * (12 bytes 00 00 00 00 00 00 00 00 00 00 FF FF, followed by the 4 bytes of the IPv4 address).
     */
    private byte[] ipv6;
    private int port;

    private NetworkAddress(Builder builder) {
        time = builder.time;
        stream = builder.stream;
        services = builder.services;
        ipv6 = builder.ipv6;
        port = builder.port;
    }

    public byte[] getIPv6() {
        return ipv6;
    }

    public int getPort() {
        return port;
    }

    public long getServices() {
        return services;
    }

    public long getStream() {
        return stream;
    }

    public long getTime() {
        return time;
    }

    public InetAddress toInetAddress() {
        try {
            return InetAddress.getByAddress(ipv6);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NetworkAddress that = (NetworkAddress) o;

        return port == that.port && Arrays.equals(ipv6, that.ipv6);
    }

    @Override
    public int hashCode() {
        int result = ipv6 != null ? Arrays.hashCode(ipv6) : 0;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return toInetAddress() + ":" + port;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        write(stream, false);
    }

    public void write(OutputStream stream, boolean light) throws IOException {
        if (!light) {
            Encode.int64(time, stream);
            Encode.int32(this.stream, stream);
        }
        Encode.int64(services, stream);
        stream.write(ipv6);
        Encode.int16(port, stream);
    }

    public static final class Builder {
        private long time;
        private long stream;
        private long services = 1;
        private byte[] ipv6;
        private int port;

        public Builder() {
        }

        public Builder time(final long time) {
            this.time = time;
            return this;
        }

        public Builder stream(final long stream) {
            this.stream = stream;
            return this;
        }

        public Builder services(final long services) {
            this.services = services;
            return this;
        }

        public Builder ip(InetAddress inetAddress) {
            byte[] addr = inetAddress.getAddress();
            if (addr.length == 16) {
                this.ipv6 = addr;
            } else if (addr.length == 4) {
                this.ipv6 = new byte[16];
                System.arraycopy(addr, 0, this.ipv6, 12, 4);
            } else {
                throw new IllegalArgumentException("Weird address " + inetAddress);
            }
            return this;
        }

        public Builder ipv6(byte[] ipv6) {
            this.ipv6 = ipv6;
            return this;
        }

        public Builder ipv6(int p00, int p01, int p02, int p03,
                            int p04, int p05, int p06, int p07,
                            int p08, int p09, int p10, int p11,
                            int p12, int p13, int p14, int p15) {
            this.ipv6 = new byte[]{
                    (byte) p00, (byte) p01, (byte) p02, (byte) p03,
                    (byte) p04, (byte) p05, (byte) p06, (byte) p07,
                    (byte) p08, (byte) p09, (byte) p10, (byte) p11,
                    (byte) p12, (byte) p13, (byte) p14, (byte) p15
            };
            return this;
        }

        public Builder ipv4(int p00, int p01, int p02, int p03) {
            this.ipv6 = new byte[]{
                    (byte) 0, (byte) 0, (byte) 0x00, (byte) 0x00,
                    (byte) 0, (byte) 0, (byte) 0x00, (byte) 0x00,
                    (byte) 0, (byte) 0, (byte) 0xff, (byte) 0xff,
                    (byte) p00, (byte) p01, (byte) p02, (byte) p03
            };
            return this;
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public NetworkAddress build() {
            return new NetworkAddress(this);
        }
    }
}
