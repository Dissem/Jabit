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
import ch.dissem.bitmessage.entity.Plaintext.Encoding;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.ports.NetworkHandler.MessageListener;
import ch.dissem.bitmessage.utils.Security;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.TreeSet;

import static ch.dissem.bitmessage.entity.Plaintext.Status.*;
import static ch.dissem.bitmessage.entity.payload.ObjectType.GET_PUBKEY;
import static ch.dissem.bitmessage.entity.payload.ObjectType.MSG;
import static ch.dissem.bitmessage.entity.payload.ObjectType.PUBKEY;
import static ch.dissem.bitmessage.utils.UnixTime.DAY;

/**
 * Created by chris on 05.04.15.
 */
public class BitmessageContext {
    public static final int CURRENT_VERSION = 3;
    private final static Logger LOG = LoggerFactory.getLogger(BitmessageContext.class);

    private final InternalContext ctx;

    private BitmessageContext(Builder builder) {
        ctx = new InternalContext(builder);
    }

    public AddressRepository addresses() {
        return ctx.getAddressRepo();
    }

    public MessageRepository messages() {
        return ctx.getMessageRepository();
    }

    public BitmessageAddress createIdentity(boolean shorter, Feature... features) {
        BitmessageAddress identity = new BitmessageAddress(new PrivateKey(
                shorter,
                ctx.getStreams()[0],
                ctx.getNetworkNonceTrialsPerByte(),
                ctx.getNetworkExtraBytes(),
                features
        ));
        ctx.getAddressRepo().save(identity);
        ctx.sendPubkey(identity, identity.getStream());
        return identity;
    }

    public void addDistributedMailingList(String address, String alias) {
        // TODO
    }

    public void send(BitmessageAddress from, BitmessageAddress to, String subject, String message) {
        if (from.getPrivateKey() == null) {
            throw new IllegalArgumentException("'From' must be an identity, i.e. have a private key.");
        }
        Plaintext msg = new Plaintext.Builder()
                .from(from)
                .to(to)
                .encoding(Encoding.SIMPLE)
                .message(subject, message)
                .build();
        if (to.getPubkey() == null) {
            requestPubkey(from, to);
            msg.setStatus(PUBKEY_REQUESTED);
            ctx.getMessageRepository().save(msg);
        } else {
            msg.setStatus(DOING_PROOF_OF_WORK);
            ctx.getMessageRepository().save(msg);
            ctx.send(
                    from,
                    to,
                    new Msg(msg),
                    +2 * DAY,
                    ctx.getNonceTrialsPerByte(to),
                    ctx.getExtraBytes(to)
            );
            msg.setStatus(SENT);
            ctx.getMessageRepository().save(msg);
        }
    }

    private void requestPubkey(BitmessageAddress requestingIdentity, BitmessageAddress address) {
        ctx.send(
                requestingIdentity,
                address,
                new GetPubkey(address),
                +28 * DAY,
                ctx.getNetworkNonceTrialsPerByte(),
                ctx.getNetworkExtraBytes()
        );
    }

    private void send(long stream, long version, ObjectPayload payload, long timeToLive) {
        try {
            long expires = UnixTime.now(+timeToLive);
            LOG.info("Expires at " + expires);
            ObjectMessage object = new ObjectMessage.Builder()
                    .stream(stream)
                    .version(version)
                    .expiresTime(expires)
                    .payload(payload)
                    .build();
            Security.doProofOfWork(object, ctx.getProofOfWorkEngine(),
                    ctx.getNetworkNonceTrialsPerByte(), ctx.getNetworkExtraBytes());
            ctx.getInventory().storeObject(object);
            ctx.getNetworkHandler().offer(object.getInventoryVector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void startup(Listener listener) {
        ctx.getNetworkHandler().start(new DefaultMessageListener(ctx, listener));
    }

    public void shutdown() {
        ctx.getNetworkHandler().stop();
    }

    public void addContact(BitmessageAddress contact) {
        ctx.getAddressRepo().save(contact);
        // TODO: search pubkey in inventory
        ctx.requestPubkey(contact);
    }

    public interface Listener {
        void receive(Plaintext plaintext);
    }

    public static final class Builder {
        int port = 8444;
        Inventory inventory;
        NodeRegistry nodeRegistry;
        NetworkHandler networkHandler;
        AddressRepository addressRepo;
        MessageRepository messageRepo;
        ProofOfWorkEngine proofOfWorkEngine;
        TreeSet<Long> streams;

        public Builder() {
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder inventory(Inventory inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder nodeRegistry(NodeRegistry nodeRegistry) {
            this.nodeRegistry = nodeRegistry;
            return this;
        }

        public Builder networkHandler(NetworkHandler networkHandler) {
            this.networkHandler = networkHandler;
            return this;
        }

        public Builder addressRepo(AddressRepository addressRepo) {
            this.addressRepo = addressRepo;
            return this;
        }

        public Builder messageRepo(MessageRepository messageRepo) {
            this.messageRepo = messageRepo;
            return this;
        }

        public Builder proofOfWorkEngine(ProofOfWorkEngine proofOfWorkEngine) {
            this.proofOfWorkEngine = proofOfWorkEngine;
            return this;
        }

        public Builder streams(Collection<Long> streams) {
            this.streams = new TreeSet<>(streams);
            return this;
        }

        public Builder streams(long... streams) {
            this.streams = new TreeSet<>();
            for (long stream : streams) {
                this.streams.add(stream);
            }
            return this;
        }

        public BitmessageContext build() {
            nonNull("inventory", inventory);
            nonNull("nodeRegistry", nodeRegistry);
            nonNull("networkHandler", networkHandler);
            nonNull("addressRepo", addressRepo);
            nonNull("messageRepo", messageRepo);
            if (streams == null) {
                streams(1);
            }
            if (proofOfWorkEngine == null) {
                proofOfWorkEngine = new MultiThreadedPOWEngine();
            }
            return new BitmessageContext(this);
        }

        private void nonNull(String name, Object o) {
            if (o == null) throw new IllegalStateException(name + " must not be null");
        }
    }

}
