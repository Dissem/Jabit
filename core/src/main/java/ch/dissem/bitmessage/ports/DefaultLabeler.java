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

import java.util.Iterator;

public class DefaultLabeler implements Labeler, InternalContext.ContextHolder {
    private InternalContext ctx;

    @Override
    public void setLabels(Plaintext msg) {
        if (msg.getType() == Plaintext.Type.BROADCAST) {
            msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.BROADCAST, Label.Type.UNREAD));
        } else {
            msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.UNREAD));
        }
    }

    @Override
    public void markAsRead(Plaintext msg) {
        Iterator<Label> iterator = msg.getLabels().iterator();
        while (iterator.hasNext()) {
            Label label = iterator.next();
            if (label.getType() == Label.Type.UNREAD) {
                iterator.remove();
            }
        }
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
