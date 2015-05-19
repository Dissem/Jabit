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
import ch.dissem.bitmessage.entity.Encrypted;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.ports.NetworkHandler;
import ch.dissem.bitmessage.utils.Security;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static ch.dissem.bitmessage.entity.Plaintext.Status.DOING_PROOF_OF_WORK;
import static ch.dissem.bitmessage.entity.Plaintext.Status.SENT;
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
    public void receive(ObjectMessage object) {
        ObjectPayload payload = object.getPayload();
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
            try {
                long expires = UnixTime.now(+28 * DAY);
                LOG.info("Expires at " + expires);
                ObjectMessage response = new ObjectMessage.Builder()
                        .stream(object.getStream())
                        .version(identity.getVersion())
                        .expiresTime(expires)
                        .payload(identity.getPubkey())
                        .build();
                Security.doProofOfWork(response, ctx.getProofOfWorkEngine(),
                        ctx.getNetworkNonceTrialsPerByte(), ctx.getNetworkExtraBytes());
                if (response.isSigned()) {
                    response.sign(identity.getPrivateKey());
                }
                if (response instanceof Encrypted) {
                    response.encrypt(Security.createPublicKey(identity.getPubkeyDecryptionKey()).getEncoded(false));
                }
                ctx.getInventory().storeObject(response);
                ctx.getNetworkHandler().offer(response.getInventoryVector());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void receive(ObjectMessage object, Pubkey pubkey) {
        BitmessageAddress address;
        try {
            if (pubkey instanceof V4Pubkey) {
                V4Pubkey v4Pubkey = (V4Pubkey) pubkey;
                address = ctx.getAddressRepo().findContact(v4Pubkey.getTag());
                if (address != null) {
                    v4Pubkey.decrypt(address.getPubkeyDecryptionKey());
                }
            } else {
                address = ctx.getAddressRepo().findContact(pubkey.getRipe());
            }
            if (address != null) {
                address.setPubkey(pubkey);
                List<Plaintext> messages = ctx.getMessageRepository().findMessages(Plaintext.Status.PUBKEY_REQUESTED, address);
                for (Plaintext msg:messages){
                    // TODO: send messages enqueued for this address
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
        } catch (IllegalArgumentException | IOException e) {
            LOG.debug(e.getMessage(), e);
        }
    }

    protected void receive(ObjectMessage object, Msg msg) {
        for (BitmessageAddress identity : ctx.getAddressRepo().getIdentities()) {
            try {
                msg.decrypt(identity.getPrivateKey().getPrivateEncryptionKey());
                msg.getPlaintext().setTo(identity);
                object.isSignatureValid(msg.getPlaintext().getFrom().getPubkey());
                ctx.getMessageRepository().save(msg.getPlaintext());
                listener.receive(msg.getPlaintext());
                break;
            } catch (IOException ignore) {
            }
        }
    }

    protected void receive(ObjectMessage object, Broadcast broadcast) {
        // TODO this should work fine as-is, but checking the tag might be more efficient
//        V5Broadcast v5 = broadcast instanceof V5Broadcast ? (V5Broadcast) broadcast : null;
        for (BitmessageAddress subscription : ctx.getAddressRepo().getSubscriptions()) {
            try {
                broadcast.decrypt(subscription.getPubkeyDecryptionKey());
                object.isSignatureValid(broadcast.getPlaintext().getFrom().getPubkey());
                listener.receive(broadcast.getPlaintext());
            } catch (IOException ignore) {
            }
        }
    }
}
