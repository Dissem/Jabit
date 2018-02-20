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

package ch.dissem.bitmessage.repository

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.ports.NodeRegistry
import ch.dissem.bitmessage.utils.UnixTime.now
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Please note that some tests fail if there is no internet connection,
 * as the initial nodes' IP addresses are determined by DNS lookup.
 */
class JdbcNodeRegistryTest : TestBase() {
    private lateinit var config: TestJdbcConfig
    private lateinit var registry: NodeRegistry

    @BeforeEach
    fun setUp() {
        config = TestJdbcConfig()
        config.reset()
        registry = JdbcNodeRegistry(config)

        registry.offerAddresses(
            Arrays.asList(
                createAddress(1, 8444, 1, now),
                createAddress(2, 8444, 1, now),
                createAddress(3, 8444, 1, now),
                createAddress(4, 8444, 2, now)
            )
        )
    }

    @Test
    fun `ensure getKnownNodes() without streams yields empty`() {
        assertTrue(registry.getKnownAddresses(10).isEmpty())
    }

    @Test
    fun `ensure predefined node is returned when database is empty`() {
        config.reset()
        val knownAddresses = registry.getKnownAddresses(2, 1)
        assertEquals(1, knownAddresses.size.toLong())
    }

    @Test
    fun `ensure known addresses are retrieved`() {
        var knownAddresses = registry.getKnownAddresses(2, 1)
        assertEquals(2, knownAddresses.size.toLong())

        knownAddresses = registry.getKnownAddresses(1000, 1)
        assertEquals(3, knownAddresses.size.toLong())
    }

    @Test
    fun `ensure offered addresses are added`() {
        registry.offerAddresses(
            Arrays.asList(
                createAddress(1, 8444, 1, now),
                createAddress(10, 8444, 1, now),
                createAddress(11, 8444, 1, now)
            )
        )

        var knownAddresses = registry.getKnownAddresses(1000, 1)
        assertEquals(5, knownAddresses.size.toLong())

        registry.offerAddresses(listOf(createAddress(1, 8445, 1, now)))

        knownAddresses = registry.getKnownAddresses(1000, 1)
        assertEquals(6, knownAddresses.size.toLong())
    }

    private fun createAddress(lastByte: Int, port: Int, stream: Long, time: Long): NetworkAddress {
        return NetworkAddress.Builder()
            .ipv6(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, lastByte)
            .port(port)
            .stream(stream)
            .time(time)
            .build()
    }
}
