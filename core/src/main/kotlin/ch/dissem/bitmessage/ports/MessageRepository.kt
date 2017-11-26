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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Status
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import java.util.*

interface MessageRepository {
    fun countUnread(label: Label?): Int

    fun getAllMessages(): List<Plaintext>

    fun getMessage(id: Any): Plaintext

    fun getMessage(iv: InventoryVector): Plaintext?

    fun getMessage(initialHash: ByteArray): Plaintext?

    fun getMessageForAck(ackData: ByteArray): Plaintext?

    /**
     * @param label to search for
     * *
     * @return a distinct list of all conversations that have at least one message with the given label.
     */
    fun findConversations(label: Label?): List<UUID>

    fun findMessages(label: Label?): List<Plaintext>

    fun findMessages(status: Status): List<Plaintext>

    fun findMessages(status: Status, recipient: BitmessageAddress): List<Plaintext>

    fun findMessages(sender: BitmessageAddress): List<Plaintext>

    fun findResponses(parent: Plaintext): List<Plaintext>

    fun findMessagesToResend(): List<Plaintext>

    fun save(message: Plaintext)

    fun remove(message: Plaintext)

    /**
     * Returns all messages with this conversation ID. The returned messages aren't sorted in any way,
     * so you may prefer to use [ch.dissem.bitmessage.utils.ConversationService.getConversation]
     * instead.

     * @param conversationId ID of the requested conversation
     * *
     * @return all messages with the given conversation ID
     */
    fun getConversation(conversationId: UUID): Collection<Plaintext>
}
