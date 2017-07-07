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

import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext

/**
 * Objects that proof of work is currently being done for.

 * @author Christian Basler
 */
interface ProofOfWorkRepository {
    fun getItem(initialHash: ByteArray): Item

    fun getItems(): List<ByteArray>

    fun putObject(objectMessage: ObjectMessage, nonceTrialsPerByte: Long, extraBytes: Long)

    fun putObject(item: Item)

    fun removeObject(initialHash: ByteArray)

    data class Item @JvmOverloads constructor(
        val objectMessage: ObjectMessage,
        val nonceTrialsPerByte: Long,
        val extraBytes: Long,
        // Needed for ACK POW calculation
        val expirationTime: Long? = 0,
        val message: Plaintext? = null
    )
}
