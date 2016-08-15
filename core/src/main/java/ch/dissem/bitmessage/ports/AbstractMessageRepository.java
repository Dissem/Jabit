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
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.utils.Strings;
import ch.dissem.bitmessage.utils.UnixTime;

import java.util.Collection;
import java.util.List;

import static ch.dissem.bitmessage.utils.SqlStrings.join;

public abstract class AbstractMessageRepository implements MessageRepository, InternalContext.ContextHolder {
    protected InternalContext ctx;

    @Override
    public void setContext(InternalContext context) {
        this.ctx = context;
    }

    protected void safeSenderIfNecessary(Plaintext message) {
        if (message.getId() == null) {
            BitmessageAddress savedAddress = ctx.getAddressRepository().getAddress(message.getFrom().getAddress());
            if (savedAddress == null) {
                ctx.getAddressRepository().save(message.getFrom());
            } else if (savedAddress.getPubkey() == null && message.getFrom().getPubkey() != null) {
                savedAddress.setPubkey(message.getFrom().getPubkey());
                ctx.getAddressRepository().save(savedAddress);
            }
        }
    }

    @Override
    public Plaintext getMessage(Object id) {
        if (id instanceof Long) {
            return single(find("id=" + id));
        } else {
            throw new IllegalArgumentException("Long expected for ID");
        }
    }

    @Override
    public Plaintext getMessage(byte[] initialHash) {
        return single(find("initial_hash=X'" + Strings.hex(initialHash) + "'"));
    }

    @Override
    public Plaintext getMessageForAck(byte[] ackData) {
        return single(find("ack_data=X'" + Strings.hex(ackData) + "' AND status='" + Plaintext.Status.SENT + "'"));
    }

    @Override
    public List<Plaintext> findMessages(Label label) {
        if (label == null) {
            return find("id NOT IN (SELECT message_id FROM Message_Label)");
        } else {
            return find("id IN (SELECT message_id FROM Message_Label WHERE label_id=" + label.getId() + ")");
        }
    }

    @Override
    public List<Plaintext> findMessages(Plaintext.Status status, BitmessageAddress recipient) {
        return find("status='" + status.name() + "' AND recipient='" + recipient.getAddress() + "'");
    }

    @Override
    public List<Plaintext> findMessages(Plaintext.Status status) {
        return find("status='" + status.name() + "'");
    }

    @Override
    public List<Plaintext> findMessages(BitmessageAddress sender) {
        return find("sender='" + sender.getAddress() + "'");
    }

    @Override
    public List<Plaintext> findMessagesToResend() {
        return find("status='" + Plaintext.Status.SENT.name() + "'" +
                " AND next_try < " + UnixTime.now());
    }

    @Override
    public List<Label> getLabels() {
        return findLabels("1=1");
    }

    @Override
    public List<Label> getLabels(Label.Type... types) {
        return findLabels("type IN (" + join(types) + ")");
    }

    protected abstract List<Label> findLabels(String where);


    protected <T> T single(Collection<T> collection) {
        switch (collection.size()) {
            case 0:
                return null;
            case 1:
                return collection.iterator().next();
            default:
                throw new ApplicationException("This shouldn't happen, found " + collection.size() +
                        " items, one or none was expected");
        }
    }

    protected abstract List<Plaintext> find(String where);
}
