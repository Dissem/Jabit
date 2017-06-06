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

package ch.dissem.bitmessage.entity.valueobject

import ch.dissem.bitmessage.entity.Streamable
import ch.dissem.bitmessage.entity.Version
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.UnixTime
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.*

/**
 * A node's address. It's written in IPv6 format.
 */
data class NetworkAddress constructor(
    var time: Long,

    /**
     * Stream number for this node
     */
    val stream: Long,

    /**
     * same service(s) listed in version
     */
    val services: Long,

    /**
     * IPv6 address. IPv4 addresses are written into the message as a 16 byte IPv4-mapped IPv6 address
     * (12 bytes 00 00 00 00 00 00 00 00 00 00 FF FF, followed by the 4 bytes of the IPv4 address).
     */
    val iPv6: ByteArray,
    val port: Int
) : Streamable {

    fun provides(service: Version.Service?): Boolean = service?.isEnabled(services) ?: false

    fun toInetAddress(): InetAddress {
        return InetAddress.getByAddress(iPv6)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetworkAddress) return false

        return port == other.port && Arrays.equals(iPv6, other.iPv6)
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(iPv6)
        result = 31 * result + port
        return result
    }

    override fun toString(): String {
        return "[" + toInetAddress() + "]:" + port
    }

    override fun write(out: OutputStream) {
        write(out, false)
    }

    fun write(out: OutputStream, light: Boolean) {
        if (!light) {
            Encode.int64(time, out)
            Encode.int32(stream, out)
        }
        Encode.int64(services, out)
        out.write(iPv6)
        Encode.int16(port.toLong(), out)
    }

    override fun write(buffer: ByteBuffer) {
        write(buffer, false)
    }

    fun write(buffer: ByteBuffer, light: Boolean) {
        if (!light) {
            Encode.int64(time, buffer)
            Encode.int32(stream, buffer)
        }
        Encode.int64(services, buffer)
        buffer.put(iPv6)
        Encode.int16(port.toLong(), buffer)
    }

    class Builder {
        internal var time: Long? = null
        internal var stream: Long = 0
        internal var services: Long = 1
        internal var ipv6: ByteArray? = null
        internal var port: Int = 0

        fun time(time: Long): Builder {
            this.time = time
            return this
        }

        fun stream(stream: Long): Builder {
            this.stream = stream
            return this
        }

        fun services(services: Long): Builder {
            this.services = services
            return this
        }

        fun ip(inetAddress: InetAddress): Builder {
            val addr = inetAddress.address
            if (addr.size == 16) {
                this.ipv6 = addr
            } else if (addr.size == 4) {
                val ipv6 = ByteArray(16)
                ipv6[10] = 0xff.toByte()
                ipv6[11] = 0xff.toByte()
                System.arraycopy(addr, 0, ipv6, 12, 4)
                this.ipv6 = ipv6
            } else {
                throw IllegalArgumentException("Weird address " + inetAddress)
            }
            return this
        }

        fun ipv6(ipv6: ByteArray): Builder {
            this.ipv6 = ipv6
            return this
        }

        fun ipv6(p00: Int, p01: Int, p02: Int, p03: Int,
                 p04: Int, p05: Int, p06: Int, p07: Int,
                 p08: Int, p09: Int, p10: Int, p11: Int,
                 p12: Int, p13: Int, p14: Int, p15: Int): Builder {
            this.ipv6 = byteArrayOf(p00.toByte(), p01.toByte(), p02.toByte(), p03.toByte(), p04.toByte(), p05.toByte(), p06.toByte(), p07.toByte(), p08.toByte(), p09.toByte(), p10.toByte(), p11.toByte(), p12.toByte(), p13.toByte(), p14.toByte(), p15.toByte())
            return this
        }

        fun ipv4(p00: Int, p01: Int, p02: Int, p03: Int): Builder {
            this.ipv6 = byteArrayOf(0.toByte(), 0.toByte(), 0x00.toByte(), 0x00.toByte(), 0.toByte(), 0.toByte(), 0x00.toByte(), 0x00.toByte(), 0.toByte(), 0.toByte(), 0xff.toByte(), 0xff.toByte(), p00.toByte(), p01.toByte(), p02.toByte(), p03.toByte())
            return this
        }

        fun port(port: Int): Builder {
            this.port = port
            return this
        }

        fun address(address: SocketAddress): Builder {
            if (address is InetSocketAddress) {
                val inetAddress = address
                ip(inetAddress.address)
                port(inetAddress.port)
            } else {
                throw IllegalArgumentException("Unknown type of address: " + address.javaClass)
            }
            return this
        }

        fun build(): NetworkAddress {
            return NetworkAddress(
                time ?: UnixTime.now, stream, services, ipv6!!, port
            )
        }
    }
}
