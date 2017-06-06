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

/**
 * Stores and provides known peers.
 */
interface NodeRegistry {
    /**
     * Removes all known nodes from registry. This should work around connection issues
     * when there are many invalid nodes in the registry.
     */
    fun clear()

    fun getKnownAddresses(limit: Int, vararg streams: Long): List<NetworkAddress>

    fun offerAddresses(addresses: List<NetworkAddress>)
}
