/*
 * Copyright 2017 Christian Basler
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

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.UnixTime
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * The 'version' command advertises this node's latest supported protocol version upon initiation.
 */
class Version constructor(
    /**
     * Identifies protocol version being used by the node. Should equal 3. Nodes should disconnect if the remote node's
     * version is lower but continue with the connection if it is higher.
     */
    val version: Int = BitmessageContext.CURRENT_VERSION,

    /**
     * bitfield of features to be enabled for this connection
     */
    val services: Long = Version.Service.getServiceFlag(Version.Service.NODE_NETWORK),

    /**
     * standard UNIX timestamp in seconds
     */
    val timestamp: Long = UnixTime.now,

    /**
     * The network address of the node receiving this message (not including the time or stream number)
     */
    val addrRecv: NetworkAddress,

    /**
     * The network address of the node emitting this message (not including the time or stream number and the ip itself
     * is ignored by the receiver)
     */
    val addrFrom: NetworkAddress,

    /**
     * Random nonce used to detect connections to self.
     */
    val nonce: Long,

    /**
     * User Agent (0x00 if string is 0 bytes long). Sending nodes must not include a user_agent longer than 5000 bytes.
     */
    val userAgent: String,

    /**
     * The stream numbers that the emitting node is interested in. Sending nodes must not include more than 160000
     * stream numbers.
     */
    val streams: LongArray = longArrayOf(1)
) : MessagePayload {

    fun provides(service: Service?): Boolean {
        return service != null && service.isEnabled(services)
    }

    override val command: MessagePayload.Command = MessagePayload.Command.VERSION

    override fun write(out: OutputStream) {
        Encode.int32(version, out)
        Encode.int64(services, out)
        Encode.int64(timestamp, out)
        addrRecv.write(out, true)
        addrFrom.write(out, true)
        Encode.int64(nonce, out)
        Encode.varString(userAgent, out)
        Encode.varIntList(streams, out)
    }

    override fun write(buffer: ByteBuffer) {
        Encode.int32(version, buffer)
        Encode.int64(services, buffer)
        Encode.int64(timestamp, buffer)
        addrRecv.write(buffer, true)
        addrFrom.write(buffer, true)
        Encode.int64(nonce, buffer)
        Encode.varString(userAgent, buffer)
        Encode.varIntList(streams, buffer)
    }

    class Builder {
        private var version: Int = 0
        private var services: Long = 0
        private var timestamp: Long = 0
        private var addrRecv: NetworkAddress? = null
        private var addrFrom: NetworkAddress? = null
        private var nonce: Long = 0
        private var userAgent: String? = null
        private var streamNumbers: LongArray? = null

        fun defaults(clientNonce: Long): Builder {
            version = BitmessageContext.CURRENT_VERSION
            services = Service.getServiceFlag(Service.NODE_NETWORK)
            timestamp = UnixTime.now
            userAgent = "/Jabit:0.0.1/"
            streamNumbers = longArrayOf(1)
            nonce = clientNonce
            return this
        }

        fun version(version: Int): Builder {
            this.version = version
            return this
        }

        fun services(vararg services: Service): Builder {
            this.services = Service.getServiceFlag(*services)
            return this
        }

        fun services(services: Long): Builder {
            this.services = services
            return this
        }

        fun timestamp(timestamp: Long): Builder {
            this.timestamp = timestamp
            return this
        }

        fun addrRecv(addrRecv: NetworkAddress): Builder {
            this.addrRecv = addrRecv
            return this
        }

        fun addrFrom(addrFrom: NetworkAddress): Builder {
            this.addrFrom = addrFrom
            return this
        }

        fun nonce(nonce: Long): Builder {
            this.nonce = nonce
            return this
        }

        fun userAgent(userAgent: String): Builder {
            this.userAgent = userAgent
            return this
        }

        fun streams(vararg streamNumbers: Long): Builder {
            this.streamNumbers = streamNumbers
            return this
        }

        fun build(): Version {
            val addrRecv = this.addrRecv
            val addrFrom = this.addrFrom
            if (addrRecv == null || addrFrom == null) {
                throw IllegalStateException("Receiving and sending address must be set")
            }

            return Version(
                version = version,
                services = services,
                timestamp = timestamp,
                addrRecv = addrRecv, addrFrom = addrFrom,
                nonce = nonce,
                userAgent = userAgent ?: "/Jabit:0.0.1/",
                streams = streamNumbers ?: longArrayOf(1)
            )
        }
    }

    enum class Service constructor(internal var flag: Long) {
        // TODO: NODE_SSL(2);
        NODE_NETWORK(1);

        fun isEnabled(flag: Long): Boolean {
            return (flag and this.flag) != 0L
        }

        companion object {
            fun getServiceFlag(vararg services: Service): Long {
                var flag: Long = 0
                for (service in services) {
                    flag = flag or service.flag
                }
                return flag
            }
        }
    }
}
