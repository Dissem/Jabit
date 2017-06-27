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

import ch.dissem.bitmessage.entity.BitmessageAddress

interface AddressRepository {
    /**
     * Returns a matching BitmessageAddress if there is one with the given ripe or tag, that
     * has no public key yet. If it doesn't exist or already has a public key, null is returned.

     * @param ripeOrTag Either ripe or tag (depending of address version) of an address with
     * *                  missing public key.
     * *
     * @return the matching address if there is one without public key, or null otherwise.
     */
    fun findContact(ripeOrTag: ByteArray): BitmessageAddress?

    fun findIdentity(ripeOrTag: ByteArray): BitmessageAddress?

    /**
     * @return all Bitmessage addresses that belong to this user, i.e. have a private key.
     */
    fun getIdentities(): List<BitmessageAddress>

    /**
     * @return all subscribed chans.
     */
    fun getChans(): List<BitmessageAddress>

    fun getSubscriptions(): List<BitmessageAddress>

    fun getSubscriptions(broadcastVersion: Long): List<BitmessageAddress>

    /**
     * @return all Bitmessage addresses that have no private key or are chans.
     */
    fun getContacts(): List<BitmessageAddress>

    /**
     * Implementations must not delete cryptographic keys if they're not provided by `address`.

     * @param address to save or update
     */
    fun save(address: BitmessageAddress)

    fun remove(address: BitmessageAddress)

    fun getAddress(address: String): BitmessageAddress?
}
