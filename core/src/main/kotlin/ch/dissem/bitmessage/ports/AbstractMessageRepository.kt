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

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.utils.SqlStrings.join
import ch.dissem.bitmessage.utils.Strings
import ch.dissem.bitmessage.utils.UnixTime
import java.util.*

abstract class AbstractMessageRepository : MessageRepository, InternalContext.ContextHolder {
    protected lateinit var ctx: InternalContext

    override fun setContext(context: InternalContext) {
        ctx = context
    }

    protected fun saveContactIfNecessary(contact: BitmessageAddress?) {
        contact?.let {
            val savedAddress = ctx.addressRepository.getAddress(it.address)
            if (savedAddress == null) {
                ctx.addressRepository.save(it)
            } else {
                if (savedAddress.pubkey == null && it.pubkey != null) {
                    savedAddress.pubkey = it.pubkey
                    ctx.addressRepository.save(savedAddress)
                }
                it.alias = savedAddress.alias
            }
        }
    }

    override fun getAllMessages() = find("1=1")

    override fun getMessage(id: Any): Plaintext {
        if (id is Long) {
            return single(find("id=" + id)) ?: throw IllegalArgumentException("There  is no message with id $id")
        } else {
            throw IllegalArgumentException("Long expected for ID")
        }
    }

    override fun getMessage(iv: InventoryVector): Plaintext? {
        return single(find("iv=X'" + Strings.hex(iv.hash) + "'"))
    }

    override fun getMessage(initialHash: ByteArray): Plaintext? {
        return single(find("initial_hash=X'" + Strings.hex(initialHash) + "'"))
    }

    override fun getMessageForAck(ackData: ByteArray): Plaintext? {
        return single(find("ack_data=X'" + Strings.hex(ackData) + "' AND status='" + Plaintext.Status.SENT + "'"))
    }

    override fun findMessages(label: Label?): List<Plaintext> {
        if (label == null) {
            return find("id NOT IN (SELECT message_id FROM Message_Label)")
        } else {
            return find("id IN (SELECT message_id FROM Message_Label WHERE label_id=" + label.id + ")")
        }
    }

    override fun findMessages(status: Plaintext.Status, recipient: BitmessageAddress): List<Plaintext> {
        return find("status='" + status.name + "' AND recipient='" + recipient.address + "'")
    }

    override fun findMessages(status: Plaintext.Status): List<Plaintext> {
        return find("status='" + status.name + "'")
    }

    override fun findMessages(sender: BitmessageAddress): List<Plaintext> {
        return find("sender='" + sender.address + "'")
    }

    override fun findMessagesToResend(): List<Plaintext> {
        return find("status='" + Plaintext.Status.SENT.name + "'" +
            " AND next_try < " + UnixTime.now)
    }

    override fun findResponses(parent: Plaintext): List<Plaintext> {
        if (parent.inventoryVector == null) {
            return emptyList()
        }
        return find("iv IN (SELECT child FROM Message_Parent"
            + " WHERE parent=X'" + Strings.hex(parent.inventoryVector!!.hash) + "')")
    }

    override fun getConversation(conversationId: UUID): List<Plaintext> {
        return find("conversation=X'" + conversationId.toString().replace("-", "") + "'")
    }

    override fun getLabels(): List<Label> {
        return findLabels("1=1")
    }

    override fun getLabels(vararg types: Label.Type): List<Label> {
        return findLabels("type IN (" + join(*types) + ")")
    }

    protected abstract fun findLabels(where: String): List<Label>


    protected fun <T> single(collection: Collection<T>): T? {
        when (collection.size) {
            0 -> return null
            1 -> return collection.iterator().next()
            else -> throw ApplicationException("This shouldn't happen, found " + collection.size +
                " items, one or none was expected")
        }
    }

    protected abstract fun find(where: String): List<Plaintext>
}
