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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Status.*
import ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST
import ch.dissem.bitmessage.entity.valueobject.Label

open class DefaultLabeler : Labeler, InternalContext.ContextHolder {
    private lateinit var ctx: InternalContext

    var listener: ((message: Plaintext, added: Collection<Label>, removed: Collection<Label>) -> Unit)? = null

    override fun setContext(context: InternalContext) {
        ctx = context
    }

    override fun setLabels(msg: Plaintext) {
        msg.status = RECEIVED
        val labelsToAdd =
            if (msg.type == BROADCAST) {
                ctx.messageRepository.getLabels(Label.Type.INBOX, Label.Type.BROADCAST, Label.Type.UNREAD)
            } else {
                ctx.messageRepository.getLabels(Label.Type.INBOX, Label.Type.UNREAD)
            }
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, emptyList())
    }

    override fun markAsDraft(msg: Plaintext) {
        msg.status = DRAFT
        val labelsToAdd = ctx.messageRepository.getLabels(Label.Type.DRAFT)
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, emptyList())
    }

    override fun markAsSending(msg: Plaintext) {
        if (msg.to != null && msg.to!!.pubkey == null) {
            msg.status = PUBKEY_REQUESTED
        } else {
            msg.status = DOING_PROOF_OF_WORK
        }
        val labelsToRemove = msg.labels.filter { it.type == Label.Type.DRAFT }
        msg.removeLabel(Label.Type.DRAFT)
        val labelsToAdd = ctx.messageRepository.getLabels(Label.Type.OUTBOX)
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, labelsToRemove)
    }

    override fun markAsSent(msg: Plaintext) {
        msg.status = SENT
        val labelsToRemove = msg.labels.filter { it.type == Label.Type.OUTBOX }
        msg.removeLabel(Label.Type.OUTBOX)
        val labelsToAdd = ctx.messageRepository.getLabels(Label.Type.SENT)
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, labelsToRemove)
    }

    override fun markAsAcknowledged(msg: Plaintext) {
        msg.status = SENT_ACKNOWLEDGED
    }

    override fun markAsRead(msg: Plaintext) {
        val labelsToRemove = msg.labels.filter { it.type == Label.Type.UNREAD }
        msg.removeLabel(Label.Type.UNREAD)
        listener?.invoke(msg, emptyList(), labelsToRemove)
    }

    override fun markAsUnread(msg: Plaintext) {
        val labelsToAdd = ctx.messageRepository.getLabels(Label.Type.UNREAD)
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, emptyList())
    }

    override fun delete(msg: Plaintext) {
        val labelsToRemove = msg.labels.filterNot { it.type == Label.Type.TRASH }
        msg.labels.clear()
        val labelsToAdd = ctx.messageRepository.getLabels(Label.Type.TRASH)
        msg.addLabels(labelsToAdd)
        listener?.invoke(msg, labelsToAdd, labelsToRemove)
    }

    override fun archive(msg: Plaintext) {
        val labelsToRemove = msg.labels.toSet()
        msg.labels.clear()
        listener?.invoke(msg, emptyList(), labelsToRemove)
    }
}
