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
import java.util.Random;

/**
 * The 'version' command advertises this node's latest supported protocol version upon initiation.
 */
public class Version implements Command {
    public static final int CURRENT = 3;
    /**
     * Identifies protocol version being used by the node. Should equal 3. Nodes should disconnect if the remote node's
     * version is lower but continue with the connection if it is higher.
     */
    private final int version;

    /**
     * bitfield of features to be enabled for this connection
     */
    private final long services;

    /**
     * standard UNIX timestamp in seconds
     */
    private final long timestamp;

    /**
     * The network address of the node receiving this message (not including the time or stream number)
     */
    private final NetworkAddress addrRecv;

    /**
     * The network address of the node emitting this message (not including the time or stream number and the ip itself
     * is ignored by the receiver)
     */
    private final NetworkAddress addrFrom;

    /**
     * Random nonce used to detect connections to self.
     */
    private final long nonce;

    /**
     * User Agent (0x00 if string is 0 bytes long). Sending nodes must not include a user_agent longer than 5000 bytes.
     */
    private final String userAgent;

    /**
     * The stream numbers that the emitting node is interested in. Sending nodes must not include more than 160000
     * stream numbers.
     */
    private final long[] streamNumbers;

    public int getVersion() {
        return version;
    }

    public long getServices() {
        return services;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public NetworkAddress getAddrRecv() {
        return addrRecv;
    }

    public NetworkAddress getAddrFrom() {
        return addrFrom;
    }

    public long getNonce() {
        return nonce;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public long[] getStreamNumbers() {
        return streamNumbers;
    }

    private Version(Builder builder) {
        version = builder.version;
        services = builder.services;
        timestamp = builder.timestamp;
        addrRecv = builder.addrRecv;
        addrFrom = builder.addrFrom;
        nonce = builder.nonce;
        userAgent = builder.userAgent;
        streamNumbers = builder.streamNumbers;
    }

    @Override
    public String getCommand() {
        return "version";
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        Encode.int32(version, stream);
        Encode.int64(services, stream);
        Encode.int64(timestamp, stream);
        addrRecv.write(stream, true);
        addrFrom.write(stream, true);
        Encode.int64(nonce, stream);
        Encode.varString(userAgent, stream);
        Encode.varIntList(streamNumbers, stream);
    }


    public static final class Builder {
        private int version;
        private long services;
        private long timestamp;
        private NetworkAddress addrRecv;
        private NetworkAddress addrFrom;
        private long nonce;
        private String userAgent;
        private long[] streamNumbers;

        public Builder() {
        }

        public Builder defaults() {
            version = CURRENT;
            services = 1;
            timestamp = System.currentTimeMillis() / 1000;
            nonce = new Random().nextInt();
            userAgent = "/Jabit:0.0.1/";
            streamNumbers = new long[]{1};
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder services(long services) {
            this.services = services;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder addrRecv(NetworkAddress addrRecv) {
            this.addrRecv = addrRecv;
            return this;
        }

        public Builder addrFrom(NetworkAddress addrFrom) {
            this.addrFrom = addrFrom;
            return this;
        }

        public Builder nonce(long nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder streams(long... streamNumbers) {
            this.streamNumbers = streamNumbers;
            return this;
        }

        public Version build() {
            return new Version(this);
        }
    }
}
