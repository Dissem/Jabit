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

package ch.dissem.bitmessage.networking

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.ports.NodeRegistry

import java.util.Arrays

/**
 * Empty [NodeRegistry] that doesn't do anything, but shouldn't break things either.
 */
internal class TestNodeRegistry(vararg nodes: NetworkAddress) : NodeRegistry {
    private val nodes: List<NetworkAddress> = listOf(*nodes)

    override fun clear() {
        // no op
    }

    override fun getKnownAddresses(limit: Int, vararg streams: Long): List<NetworkAddress> {
        return nodes
    }

    override fun offerAddresses(nodes: List<NetworkAddress>) {
        // Ignore
    }

    override fun update(node: NetworkAddress) {
        // Ignore
    }

    override fun remove(node: NetworkAddress) {
        // Ignore
    }

    override fun cleanup() {
        // Ignore
    }
}
