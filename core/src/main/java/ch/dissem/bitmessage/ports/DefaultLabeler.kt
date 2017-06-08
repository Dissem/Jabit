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

open class DefaultLabeler : Labeler {
    private var ctx by InternalContext

    override fun setLabels(msg: Plaintext) {
        msg.status = RECEIVED
        if (msg.type == BROADCAST) {
            msg.addLabels(ctx.messageRepository.getLabels(Label.Type.INBOX, Label.Type.BROADCAST, Label.Type.UNREAD))
        } else {
            msg.addLabels(ctx.messageRepository.getLabels(Label.Type.INBOX, Label.Type.UNREAD))
        }
    }

    override fun markAsDraft(msg: Plaintext) {
        msg.status = DRAFT
        msg.addLabels(ctx.messageRepository.getLabels(Label.Type.DRAFT))
    }

    override fun markAsSending(msg: Plaintext) {
        if (msg.to != null && msg.to!!.pubkey == null) {
            msg.status = PUBKEY_REQUESTED
        } else {
            msg.status = DOING_PROOF_OF_WORK
        }
        msg.removeLabel(Label.Type.DRAFT)
        msg.addLabels(ctx.messageRepository.getLabels(Label.Type.OUTBOX))
    }

    override fun markAsSent(msg: Plaintext) {
        msg.status = SENT
        msg.removeLabel(Label.Type.OUTBOX)
        msg.addLabels(ctx.messageRepository.getLabels(Label.Type.SENT))
    }

    override fun markAsAcknowledged(msg: Plaintext) {
        msg.status = SENT_ACKNOWLEDGED
    }

    override fun markAsRead(msg: Plaintext) {
        msg.removeLabel(Label.Type.UNREAD)
    }

    override fun markAsUnread(msg: Plaintext) {
        msg.addLabels(ctx.messageRepository.getLabels(Label.Type.UNREAD))
    }

    override fun delete(msg: Plaintext) {
        msg.labels.clear()
        msg.addLabels(ctx.messageRepository.getLabels(Label.Type.TRASH))
    }

    override fun archive(msg: Plaintext) {
        msg.labels.clear()
    }
}
