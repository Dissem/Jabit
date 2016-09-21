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

import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.payload.Broadcast;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Property;
import ch.dissem.bitmessage.utils.TTL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static ch.dissem.bitmessage.InternalContext.NETWORK_EXTRA_BYTES;
import static ch.dissem.bitmessage.InternalContext.NETWORK_NONCE_TRIALS_PER_BYTE;
import static ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST;
import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.UnixTime.*;

/**
 * <p>Use this class if you want to create a Bitmessage client.</p>
 * You'll need the Builder to create a BitmessageContext, and set the following properties:
 * <ul>
 * <li>addressRepo</li>
 * <li>inventory</li>
 * <li>nodeRegistry</li>
 * <li>networkHandler</li>
 * <li>messageRepo</li>
 * <li>streams</li>
 * </ul>
 * <p>The default implementations in the different module builds can be used.</p>
 * <p>The port defaults to 8444 (the default Bitmessage port)</p>
 */
public class BitmessageContext {
    public static final int CURRENT_VERSION = 3;
    private final static Logger LOG = LoggerFactory.getLogger(BitmessageContext.class);

    private final InternalContext ctx;

    private final Labeler labeler;

    private final boolean sendPubkeyOnIdentityCreation;

    private BitmessageContext(Builder builder) {
        if (builder.listener instanceof Listener.WithContext) {
            ((Listener.WithContext) builder.listener).setContext(this);
        }
        ctx = new InternalContext(builder);
        labeler = builder.labeler;
        ctx.getProofOfWorkService().doMissingProofOfWork(30_000); // TODO: this should be configurable
        sendPubkeyOnIdentityCreation = builder.sendPubkeyOnIdentityCreation;
    }

    public AddressRepository addresses() {
        return ctx.getAddressRepository();
    }

    public MessageRepository messages() {
        return ctx.getMessageRepository();
    }

    public Labeler labeler() {
        return labeler;
    }

    public BitmessageAddress createIdentity(boolean shorter, Feature... features) {
        final BitmessageAddress identity = new BitmessageAddress(new PrivateKey(
            shorter,
            ctx.getStreams()[0],
            NETWORK_NONCE_TRIALS_PER_BYTE,
            NETWORK_EXTRA_BYTES,
            features
        ));
        ctx.getAddressRepository().save(identity);
        if (sendPubkeyOnIdentityCreation) {
            ctx.sendPubkey(identity, identity.getStream());
        }
        return identity;
    }

    public BitmessageAddress joinChan(String passphrase, String address) {
        BitmessageAddress chan = BitmessageAddress.chan(address, passphrase);
        chan.setAlias(passphrase);
        ctx.getAddressRepository().save(chan);
        return chan;
    }

    public BitmessageAddress createChan(String passphrase) {
        // FIXME: hardcoded stream number
        BitmessageAddress chan = BitmessageAddress.chan(1, passphrase);
        ctx.getAddressRepository().save(chan);
        return chan;
    }

    public List<BitmessageAddress> createDeterministicAddresses(
        String passphrase, int numberOfAddresses, long version, long stream, boolean shorter) {
        List<BitmessageAddress> result = BitmessageAddress.deterministic(
            passphrase, numberOfAddresses, version, stream, shorter);
        for (int i = 0; i < result.size(); i++) {
            BitmessageAddress address = result.get(i);
            address.setAlias("deterministic (" + (i + 1) + ")");
            ctx.getAddressRepository().save(address);
        }
        return result;
    }

    public void broadcast(final BitmessageAddress from, final String subject, final String message) {
        Plaintext msg = new Plaintext.Builder(BROADCAST)
            .from(from)
            .message(subject, message)
            .build();
        send(msg);
    }

    public void send(final BitmessageAddress from, final BitmessageAddress to, final String subject, final String message) {
        if (from.getPrivateKey() == null) {
            throw new IllegalArgumentException("'From' must be an identity, i.e. have a private key.");
        }
        Plaintext msg = new Plaintext.Builder(MSG)
            .from(from)
            .to(to)
            .message(subject, message)
            .build();
        send(msg);
    }

    public void send(final Plaintext msg) {
        if (msg.getFrom() == null || msg.getFrom().getPrivateKey() == null) {
            throw new IllegalArgumentException("'From' must be an identity, i.e. have a private key.");
        }
        labeler().markAsSending(msg);
        BitmessageAddress to = msg.getTo();
        if (to != null) {
            if (to.getPubkey() == null) {
                LOG.info("Public key is missing from recipient. Requesting.");
                ctx.requestPubkey(to);
            }
            if (to.getPubkey() == null) {
                ctx.getMessageRepository().save(msg);
            }
        }
        if (to == null || to.getPubkey() != null) {
            LOG.info("Sending message.");
            ctx.getMessageRepository().save(msg);
            if (msg.getType() == MSG) {
                ctx.send(msg);
            } else {
                ctx.send(
                    msg.getFrom(),
                    to,
                    Factory.getBroadcast(msg),
                    msg.getTTL()
                );
            }
        }
    }

    public void startup() {
        ctx.getNetworkHandler().start();
    }

    public void shutdown() {
        ctx.getNetworkHandler().stop();
    }

    /**
     * @param host             a trusted node that must be reliable (it's used for every synchronization)
     * @param port             of the trusted host, default is 8444
     * @param timeoutInSeconds synchronization should end no later than about 5 seconds after the timeout elapsed, even
     *                         if not all objects were fetched
     * @param wait             waits for the synchronization thread to finish
     */
    public void synchronize(InetAddress host, int port, long timeoutInSeconds, boolean wait) {
        Future<?> future = ctx.getNetworkHandler().synchronize(host, port, timeoutInSeconds);
        if (wait) {
            try {
                future.get();
            } catch (InterruptedException e) {
                LOG.info("Thread was interrupted. Trying to shut down synchronization and returning.");
                future.cancel(true);
            } catch (CancellationException | ExecutionException e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

    /**
     * Send a custom message to a specific node (that should implement handling for this message type) and returns
     * the response, which in turn is expected to be a {@link CustomMessage}.
     *
     * @param server  the node's address
     * @param port    the node's port
     * @param request the request
     * @return the response
     */
    public CustomMessage send(InetAddress server, int port, CustomMessage request) {
        return ctx.getNetworkHandler().send(server, port, request);
    }

    /**
     * Removes expired objects from the inventory. You should call this method regularly,
     * e.g. daily and on each shutdown.
     */
    public void cleanup() {
        ctx.getInventory().cleanup();
    }

    /**
     * Sends messages again whose time to live expired without being acknowledged. (And whose
     * recipient is expected to send acknowledgements.
     * <p>
     * You should call this method regularly, but be aware of the following:
     * <ul>
     * <li>As messages might be sent, POW will be done. It is therefore not advised to
     * call it on shutdown.</li>
     * <li>It shouldn't be called right after startup, as it's possible the missing
     * acknowledgement was sent while the client was offline.</li>
     * <li>Other than that, the call isn't expensive as long as there is no message
     * to send, so it might be a good idea to just call it every few minutes.</li>
     * </ul>
     */
    public void resendUnacknowledgedMessages() {
        ctx.resendUnacknowledged();
    }

    public boolean isRunning() {
        return ctx.getNetworkHandler().isRunning();
    }

    public void addContact(BitmessageAddress contact) {
        ctx.getAddressRepository().save(contact);
        if (contact.getPubkey() == null) {
            ctx.requestPubkey(contact);
        }
    }

    public void addSubscribtion(BitmessageAddress address) {
        address.setSubscribed(true);
        ctx.getAddressRepository().save(address);
        tryToFindBroadcastsForAddress(address);
    }

    private void tryToFindBroadcastsForAddress(BitmessageAddress address) {
        for (ObjectMessage object : ctx.getInventory().getObjects(address.getStream(), Broadcast.getVersion(address), ObjectType.BROADCAST)) {
            try {
                Broadcast broadcast = (Broadcast) object.getPayload();
                broadcast.decrypt(address);
                // This decrypts it twice, but on the other hand it doesn't try to decrypt the objects with
                // other subscriptions and the interface stays as simple as possible.
                ctx.getNetworkListener().receive(object);
            } catch (DecryptionFailedException ignore) {
            } catch (Exception e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

    public Property status() {
        return new Property("status", null,
            ctx.getNetworkHandler().getNetworkStatus(),
            new Property("unacknowledged", ctx.getMessageRepository().findMessagesToResend().size())
        );
    }

    /**
     * Returns the {@link InternalContext} - normally you wouldn't need it,
     * unless you are doing something crazy with the protocol.
     */
    public InternalContext internals() {
        return ctx;
    }

    public interface Listener {
        void receive(Plaintext plaintext);

        /**
         * A message listener that needs a {@link BitmessageContext}, i.e. for implementing some sort of chat bot.
         */
        interface WithContext extends Listener {
            void setContext(BitmessageContext ctx);
        }
    }

    public static final class Builder {
        int port = 8444;
        Inventory inventory;
        NodeRegistry nodeRegistry;
        NetworkHandler networkHandler;
        AddressRepository addressRepo;
        MessageRepository messageRepo;
        ProofOfWorkRepository proofOfWorkRepository;
        ProofOfWorkEngine proofOfWorkEngine;
        Cryptography cryptography;
        CustomCommandHandler customCommandHandler;
        Labeler labeler;
        Listener listener;
        int connectionLimit = 150;
        long connectionTTL = 30 * MINUTE;
        boolean sendPubkeyOnIdentityCreation = true;

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

        public Builder powRepo(ProofOfWorkRepository proofOfWorkRepository) {
            this.proofOfWorkRepository = proofOfWorkRepository;
            return this;
        }

        public Builder cryptography(Cryptography cryptography) {
            this.cryptography = cryptography;
            return this;
        }

        public Builder customCommandHandler(CustomCommandHandler handler) {
            this.customCommandHandler = handler;
            return this;
        }

        public Builder proofOfWorkEngine(ProofOfWorkEngine proofOfWorkEngine) {
            this.proofOfWorkEngine = proofOfWorkEngine;
            return this;
        }

        public Builder labeler(Labeler labeler) {
            this.labeler = labeler;
            return this;
        }

        public Builder listener(Listener listener) {
            this.listener = listener;
            return this;
        }

        public Builder connectionLimit(int connectionLimit) {
            this.connectionLimit = connectionLimit;
            return this;
        }

        public Builder connectionTTL(int hours) {
            this.connectionTTL = hours * HOUR;
            return this;
        }

        /**
         * By default a client will send the public key when an identity is being created. On weaker devices
         * this behaviour might not be desirable.
         */
        public Builder doNotSendPubkeyOnIdentityCreation() {
            this.sendPubkeyOnIdentityCreation = false;
            return this;
        }

        /**
         * Time to live in seconds for public keys the client sends. Defaults to the maximum of 28 days,
         * but on weak devices smaller values might be desirable.
         * <p>
         * Please be aware that this might cause some problems where you can't receive a message (the
         * sender can't receive your public key) in some special situations. Also note that it's probably
         * not a good idea to set it too low.
         * </p>
         *
         * @deprecated use {@link TTL#pubkey(long)} instead.
         */
        public Builder pubkeyTTL(long days) {
            if (days < 0 || days > 28 * DAY) throw new IllegalArgumentException("TTL must be between 1 and 28 days");
            TTL.pubkey(days);
            return this;
        }

        public BitmessageContext build() {
            nonNull("inventory", inventory);
            nonNull("nodeRegistry", nodeRegistry);
            nonNull("networkHandler", networkHandler);
            nonNull("addressRepo", addressRepo);
            nonNull("messageRepo", messageRepo);
            nonNull("proofOfWorkRepo", proofOfWorkRepository);
            if (proofOfWorkEngine == null) {
                proofOfWorkEngine = new MultiThreadedPOWEngine();
            }
            if (labeler == null) {
                labeler = new DefaultLabeler();
            }
            if (customCommandHandler == null) {
                customCommandHandler = new CustomCommandHandler() {
                    @Override
                    public MessagePayload handle(CustomMessage request) {
                        throw new IllegalStateException(
                            "Received custom request, but no custom command handler configured.");
                    }
                };
            }
            return new BitmessageContext(this);
        }

        private void nonNull(String name, Object o) {
            if (o == null) throw new IllegalStateException(name + " must not be null");
        }
    }

}
