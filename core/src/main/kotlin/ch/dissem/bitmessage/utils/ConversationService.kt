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

package ch.dissem.bitmessage.utils

import ch.dissem.bitmessage.entity.Conversation
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.ports.MessageRepository
import java.util.*
import java.util.Collections
import java.util.regex.Pattern
import java.util.regex.Pattern.CASE_INSENSITIVE

/**
 * Service that helps with conversations.
 */
class ConversationService(private val messageRepository: MessageRepository) {

    private val SUBJECT_PREFIX = Pattern.compile("^(re|fwd?):\\s*", CASE_INSENSITIVE)

    fun findConversations(label: Label?, offset: Int = 0, limit: Int = 0, conversationLimit: Int = 10) =
        messageRepository.findConversations(label, offset, limit)
            .map { getConversation(it, conversationLimit) }

    /**
     * Retrieve the whole conversation from one single message. If the message isn't part
     * of a conversation, a singleton list containing the given message is returned. Otherwise
     * it's the same as [.getConversation]

     * @param message
     * *
     * @return a list of messages that belong to the same conversation.
     */
    fun getConversation(message: Plaintext): Conversation {
        return getConversation(message.conversationId)
    }

    private fun sorted(collection: Collection<Plaintext>): LinkedList<Plaintext> {
        val result = LinkedList(collection)
        Collections.sort(result, Comparator<Plaintext> { o1, o2 ->
            return@Comparator when {
                o1.received === o2.received -> 0
                o1.received == null -> -1
                o2.received == null -> 1
                else -> -o1.received.compareTo(o2.received)
            }
        })
        return result
    }

    fun getConversation(conversationId: UUID, limit: Int = 0): Conversation {
        val messages = sorted(messageRepository.getConversation(conversationId, 0, limit))
        val map = HashMap<InventoryVector, Plaintext>(messages.size)
        for (message in messages) {
            message.inventoryVector?.let {
                map.put(it, message)
            }
        }

        val result = LinkedList<Plaintext>()
        while (!messages.isEmpty()) {
            val last = messages.poll()
            val pos = lastParentPosition(last, result)
            result.add(pos, last)
            addAncestors(last, result, messages, map)
        }
        return Conversation(conversationId, getSubject(result) ?: "", result)
    }

    fun getSubject(conversation: List<Plaintext>): String? {
        if (conversation.isEmpty()) {
            return null
        }
        // TODO: this has room for improvement
        val subject = conversation[0].subject
        val matcher = SUBJECT_PREFIX.matcher(subject!!)

        return if (matcher.find()) {
            subject.substring(matcher.end())
        } else {
            subject
        }.trim { it <= ' ' }
    }

    private fun lastParentPosition(child: Plaintext, messages: LinkedList<Plaintext>): Int {
        val plaintextIterator = messages.descendingIterator()
        var i = 0
        while (plaintextIterator.hasNext()) {
            val next = plaintextIterator.next()
            if (isParent(next, child)) {
                break
            }
            i++
        }
        return messages.size - i
    }

    private fun isParent(item: Plaintext, child: Plaintext): Boolean {
        return child.parents.firstOrNull { it == item.inventoryVector } != null
    }

    private fun addAncestors(
        message: Plaintext,
        result: LinkedList<Plaintext>,
        messages: LinkedList<Plaintext>,
        map: MutableMap<InventoryVector, Plaintext>
    ) {
        for (parentKey in message.parents) {
            map.remove(parentKey)?.let {
                messages.remove(it)
                result.addFirst(it)
                addAncestors(it, result, messages, map)
            }
        }
    }
}
