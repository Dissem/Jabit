/*
 * Copyright 2015 Christian Basler
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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static ch.dissem.bitmessage.entity.Plaintext.Status.*;
import static ch.dissem.bitmessage.utils.UnixTime.DAY;

class DefaultMessageListener implements NetworkHandler.MessageListener {
    private final static Logger LOG = LoggerFactory.getLogger(DefaultMessageListener.class);
    private final InternalContext ctx;
    private final BitmessageContext.Listener listener;

    public DefaultMessageListener(InternalContext context, BitmessageContext.Listener listener) {
        this.ctx = context;
        this.listener = listener;
    }

    @Override
    public void receive(ObjectMessage object) throws IOException {
        ObjectPayload payload = object.getPayload();
        if (payload.getType() == null) return;

        switch (payload.getType()) {
            case GET_PUBKEY: {
                receive(object, (GetPubkey) payload);
                break;
            }
            case PUBKEY: {
                receive(object, (Pubkey) payload);
                break;
            }
            case MSG: {
                receive(object, (Msg) payload);
                break;
            }
            case BROADCAST: {
                receive(object, (Broadcast) payload);
                break;
            }
        }
    }

    protected void receive(ObjectMessage object, GetPubkey getPubkey) {
        BitmessageAddress identity = ctx.getAddressRepo().findIdentity(getPubkey.getRipeTag());
        if (identity != null && identity.getPrivateKey() != null) {
            LOG.debug("Got pubkey request for identity " + identity);
            ctx.sendPubkey(identity, object.getStream());
        }
    }

    protected void receive(ObjectMessage object, Pubkey pubkey) throws IOException {
        BitmessageAddress address;
        try {
            if (pubkey instanceof V4Pubkey) {
                V4Pubkey v4Pubkey = (V4Pubkey) pubkey;
                address = ctx.getAddressRepo().findContact(v4Pubkey.getTag());
                if (address != null) {
                    v4Pubkey.decrypt(address.getPublicDecryptionKey());
                }
            } else {
                address = ctx.getAddressRepo().findContact(pubkey.getRipe());
            }
            if (address != null) {
                address.setPubkey(pubkey);
                LOG.debug("Got pubkey for contact " + address);
                ctx.getAddressRepo().save(address);
                List<Plaintext> messages = ctx.getMessageRepository().findMessages(Plaintext.Status.PUBKEY_REQUESTED, address);
                LOG.debug("Sending " + messages.size() + " messages for contact " + address);
                for (Plaintext msg : messages) {
                    msg.setStatus(DOING_PROOF_OF_WORK);
                    ctx.getMessageRepository().save(msg);
                    ctx.send(
                            msg.getFrom(),
                            msg.getTo(),
                            new Msg(msg),
                            +2 * DAY,
                            ctx.getNonceTrialsPerByte(msg.getTo()),
                            ctx.getExtraBytes(msg.getTo())
                    );
                    msg.setStatus(SENT);
                    ctx.getMessageRepository().save(msg);
                }
            }
        } catch (DecryptionFailedException ignore) {
        }
    }

    protected void receive(ObjectMessage object, Msg msg) throws IOException {
        for (BitmessageAddress identity : ctx.getAddressRepo().getIdentities()) {
            try {
                msg.decrypt(identity.getPrivateKey().getPrivateEncryptionKey());
                msg.getPlaintext().setTo(identity);
                if (!object.isSignatureValid(msg.getPlaintext().getFrom().getPubkey())) {
                    LOG.warn("Msg with IV " + object.getInventoryVector() + " was successfully decrypted, but signature check failed. Ignoring.");
                } else {
                    msg.getPlaintext().setStatus(RECEIVED);
                    msg.getPlaintext().addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.UNREAD));
                    msg.getPlaintext().setInventoryVector(object.getInventoryVector());
                    ctx.getMessageRepository().save(msg.getPlaintext());
                    listener.receive(msg.getPlaintext());
                }
                break;
            } catch (DecryptionFailedException ignore) {
            }
        }
    }

    protected void receive(ObjectMessage object, Broadcast broadcast) throws IOException {
        byte[] tag = broadcast instanceof V5Broadcast ? ((V5Broadcast) broadcast).getTag() : null;
        for (BitmessageAddress subscription : ctx.getAddressRepo().getSubscriptions(broadcast.getVersion())) {
            if (tag != null && !Arrays.equals(tag, subscription.getTag())) {
                continue;
            }
            try {
                broadcast.decrypt(subscription.getPublicDecryptionKey());
                if (!object.isSignatureValid(broadcast.getPlaintext().getFrom().getPubkey())) {
                    LOG.warn("Broadcast with IV " + object.getInventoryVector() + " was successfully decrypted, but signature check failed. Ignoring.");
                } else {
                    broadcast.getPlaintext().setStatus(RECEIVED);
                    broadcast.getPlaintext().addLabels(ctx.getMessageRepository().getLabels(Label.Type.INBOX, Label.Type.BROADCAST, Label.Type.UNREAD));
                    broadcast.getPlaintext().setInventoryVector(object.getInventoryVector());
                    ctx.getMessageRepository().save(broadcast.getPlaintext());
                    listener.receive(broadcast.getPlaintext());
                }
            } catch (DecryptionFailedException ignore) {
            }
        }
    }
}
