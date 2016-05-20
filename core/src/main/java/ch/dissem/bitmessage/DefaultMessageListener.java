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
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.ports.Labeler;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.TTL;
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
    private final Labeler labeler;
    private final BitmessageContext.Listener listener;

    public DefaultMessageListener(InternalContext context, Labeler labeler, BitmessageContext.Listener listener) {
        this.ctx = context;
        this.labeler = labeler;
        this.listener = listener;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void receive(ObjectMessage object) throws IOException {
        ObjectPayload payload = object.getPayload();
        if (payload.getType() == null) {
            if (payload instanceof GenericPayload) {
                receive((GenericPayload) payload);
            }
            return;
        }

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
            default: {
                throw new IllegalArgumentException("Unknown payload type " + payload.getType());
            }
        }
    }

    protected void receive(ObjectMessage object, GetPubkey getPubkey) {
        BitmessageAddress identity = ctx.getAddressRepository().findIdentity(getPubkey.getRipeTag());
        if (identity != null && identity.getPrivateKey() != null && !identity.isChan()) {
            LOG.info("Got pubkey request for identity " + identity);
            // FIXME: only send pubkey if it wasn't sent in the last 28 days
            ctx.sendPubkey(identity, object.getStream());
        }
    }

    protected void receive(ObjectMessage object, Pubkey pubkey) throws IOException {
        BitmessageAddress address;
        try {
            if (pubkey instanceof V4Pubkey) {
                V4Pubkey v4Pubkey = (V4Pubkey) pubkey;
                address = ctx.getAddressRepository().findContact(v4Pubkey.getTag());
                if (address != null) {
                    v4Pubkey.decrypt(address.getPublicDecryptionKey());
                }
            } else {
                address = ctx.getAddressRepository().findContact(pubkey.getRipe());
            }
            if (address != null) {
                updatePubkey(address, pubkey);
            }
        } catch (DecryptionFailedException ignore) {
        }
    }

    private void updatePubkey(BitmessageAddress address, Pubkey pubkey) {
        address.setPubkey(pubkey);
        LOG.info("Got pubkey for contact " + address);
        ctx.getAddressRepository().save(address);
        List<Plaintext> messages = ctx.getMessageRepository().findMessages(PUBKEY_REQUESTED, address);
        LOG.info("Sending " + messages.size() + " messages for contact " + address);
        for (Plaintext msg : messages) {
            ctx.getLabeler().markAsSending(msg);
            ctx.getMessageRepository().save(msg);
            ctx.send(msg);
        }
    }

    protected void receive(ObjectMessage object, Msg msg) throws IOException {
        for (BitmessageAddress identity : ctx.getAddressRepository().getIdentities()) {
            try {
                msg.decrypt(identity.getPrivateKey().getPrivateEncryptionKey());
                Plaintext plaintext = msg.getPlaintext();
                plaintext.setTo(identity);
                if (!object.isSignatureValid(plaintext.getFrom().getPubkey())) {
                    LOG.warn("Msg with IV " + object.getInventoryVector() + " was successfully decrypted, but signature check failed. Ignoring.");
                } else {
                    receive(object.getInventoryVector(), plaintext);
                }
                break;
            } catch (DecryptionFailedException ignore) {
            }
        }
    }

    protected void receive(GenericPayload ack) {
        if (ack.getData().length == Msg.ACK_LENGTH) {
            Plaintext msg = ctx.getMessageRepository().getMessageForAck(ack.getData());
            if (msg != null) {
                ctx.getLabeler().markAsAcknowledged(msg);
                ctx.getMessageRepository().save(msg);
            }
        }
    }

    protected void receive(ObjectMessage object, Broadcast broadcast) throws IOException {
        byte[] tag = broadcast instanceof V5Broadcast ? ((V5Broadcast) broadcast).getTag() : null;
        for (BitmessageAddress subscription : ctx.getAddressRepository().getSubscriptions(broadcast.getVersion())) {
            if (tag != null && !Arrays.equals(tag, subscription.getTag())) {
                continue;
            }
            try {
                broadcast.decrypt(subscription.getPublicDecryptionKey());
                if (!object.isSignatureValid(broadcast.getPlaintext().getFrom().getPubkey())) {
                    LOG.warn("Broadcast with IV " + object.getInventoryVector() + " was successfully decrypted, but signature check failed. Ignoring.");
                } else {
                    receive(object.getInventoryVector(), broadcast.getPlaintext());
                }
            } catch (DecryptionFailedException ignore) {
            }
        }
    }

    protected void receive(InventoryVector iv, Plaintext msg) {
        msg.setInventoryVector(iv);
        labeler.setLabels(msg);
        ctx.getMessageRepository().save(msg);
        listener.receive(msg);
        updatePubkey(msg.getFrom(), msg.getFrom().getPubkey());

        if (msg.getType() == Plaintext.Type.MSG && msg.getTo().has(Pubkey.Feature.DOES_ACK)) {
            ObjectMessage ack = msg.getAckMessage();
            if (ack != null) {
                ctx.getInventory().storeObject(ack);
                ctx.getNetworkHandler().offer(ack.getInventoryVector());
            }
        }
    }
}
