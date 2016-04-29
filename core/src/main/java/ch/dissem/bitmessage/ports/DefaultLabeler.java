/*
 * Copyright 2016 Christian Basler
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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.Label;

import static ch.dissem.bitmessage.entity.Plaintext.Status.*;

public class DefaultLabeler implements Labeler, InternalContext.ContextHolder {
    private InternalContext ctx;

    @Override
    public void setLabels(Plaintext msg) {
        msg.setStatus(RECEIVED);
        if (msg.getType() == Plaintext.Type.BROADCAST) {
            msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.BROADCAST, Label.Type.UNREAD));
        } else {
            msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.UNREAD));
        }
    }

    @Override
    public void markAsDraft(Plaintext msg) {
        msg.setStatus(DRAFT);
        msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.DRAFT));
    }

    @Override
    public void markAsSending(Plaintext msg) {
        if (msg.getTo() != null || msg.getTo().getPubkey() == null) {
            msg.setStatus(PUBKEY_REQUESTED);
        } else {
            msg.setStatus(DOING_PROOF_OF_WORK);
        }
        msg.removeLabel(Label.Type.DRAFT);
        msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.OUTBOX));
    }

    @Override
    public void markAsSent(Plaintext msg) {
        msg.setStatus(SENT);
        msg.removeLabel(Label.Type.OUTBOX);
        msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.SENT));
    }

    @Override
    public void markAsAcknowledged(Plaintext msg) {
        msg.setStatus(SENT_ACKNOWLEDGED);
    }

    @Override
    public void markAsRead(Plaintext msg) {
        msg.removeLabel(Label.Type.UNREAD);
    }

    @Override
    public void markAsUnread(Plaintext msg) {
        msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.UNREAD));
    }

    @Override
    public void delete(Plaintext msg) {
        msg.getLabels().clear();
        msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.TRASH));
    }

    @Override
    public void archive(Plaintext msg) {
        msg.getLabels().clear();
    }

    @Override
    public void setContext(InternalContext ctx) {
        this.ctx = ctx;
    }
}
