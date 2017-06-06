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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.util.*

/**
 * Helper class to kick start node registries.
 */
object NodeRegistryHelper {
    private val LOG = LoggerFactory.getLogger(NodeRegistryHelper::class.java)

    @JvmStatic
    fun loadStableNodes(): Map<Long, Set<NetworkAddress>> {
        javaClass.classLoader.getResourceAsStream("nodes.txt").use { `in` ->
            val scanner = Scanner(`in`)
            var stream: Long = 0
            val result = HashMap<Long, Set<NetworkAddress>>()
            var streamSet: MutableSet<NetworkAddress>? = null
            while (scanner.hasNext()) {
                try {
                    val line = scanner.nextLine().trim { it <= ' ' }
                    if (line.startsWith("[stream")) {
                        stream = java.lang.Long.parseLong(line.substring(8, line.lastIndexOf(']')))
                        streamSet = HashSet<NetworkAddress>()
                        result.put(stream, streamSet)
                    } else if (streamSet != null && !line.isEmpty() && !line.startsWith("#")) {
                        val portIndex = line.lastIndexOf(':')
                        val inetAddresses = InetAddress.getAllByName(line.substring(0, portIndex))
                        val port = Integer.valueOf(line.substring(portIndex + 1))!!
                        for (inetAddress in inetAddresses) {
                            streamSet.add(NetworkAddress.Builder().ip(inetAddress).port(port).stream(stream).build())
                        }
                    }
                } catch (e: IOException) {
                    LOG.warn(e.message, e)
                }
            }
            if (LOG.isDebugEnabled) {
                for ((key, value) in result) {
                    LOG.debug("Stream " + key + ": loaded " + value.size + " bootstrap nodes.")
                }
            }
            return result
        }
    }
}
