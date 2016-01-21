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
import ch.dissem.bitmessage.entity.payload.*;
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.entity.Plaintext.Status.*;
import static ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST;
import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.UnixTime.DAY;
import static ch.dissem.bitmessage.utils.UnixTime.HOUR;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;

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

    private final ExecutorService pool;

    private final InternalContext ctx;

    private final Listener listener;
    private final NetworkHandler.MessageListener networkListener;

    private final boolean sendPubkeyOnIdentityCreation;

    private BitmessageContext(Builder builder) {
        ctx = new InternalContext(builder);
        listener = builder.listener;
        networkListener = new DefaultMessageListener(ctx, listener);

        // As this thread is used for parts that do POW, which itself uses parallel threads, only
        // one should be executed at any time.
        pool = Executors.newFixedThreadPool(1);

        sendPubkeyOnIdentityCreation = builder.sendPubkeyOnIdentityCreation;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ctx.getProofOfWorkService().doMissingProofOfWork();
            }
        }, 30_000); // After 30 seconds
    }

    public AddressRepository addresses() {
        return ctx.getAddressRepository();
    }

    public MessageRepository messages() {
        return ctx.getMessageRepository();
    }

    public BitmessageAddress createIdentity(boolean shorter, Feature... features) {
        final BitmessageAddress identity = new BitmessageAddress(new PrivateKey(
                shorter,
                ctx.getStreams()[0],
                ctx.getNetworkNonceTrialsPerByte(),
                ctx.getNetworkExtraBytes(),
                features
        ));
        ctx.getAddressRepository().save(identity);
        if (sendPubkeyOnIdentityCreation) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    ctx.sendPubkey(identity, identity.getStream());
                }
            });
        }
        return identity;
    }

    public void addDistributedMailingList(String address, String alias) {
        // TODO
        throw new RuntimeException("not implemented");
    }

    public void broadcast(final BitmessageAddress from, final String subject, final String message) {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                Plaintext msg = new Plaintext.Builder(BROADCAST)
                        .from(from)
                        .message(subject, message)
                        .build();

                LOG.info("Sending message.");
                msg.setStatus(DOING_PROOF_OF_WORK);
                ctx.getMessageRepository().save(msg);
                ctx.send(
                        from,
                        from,
                        Factory.getBroadcast(from, msg),
                        +2 * DAY
                );
                msg.setStatus(SENT);
                msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.BROADCAST, Label.Type.SENT));
                ctx.getMessageRepository().save(msg);
            }
        });
    }

    public void send(final BitmessageAddress from, final BitmessageAddress to, final String subject, final String message) {
        if (from.getPrivateKey() == null) {
            throw new IllegalArgumentException("'From' must be an identity, i.e. have a private key.");
        }
        pool.submit(new Runnable() {
            @Override
            public void run() {
                Plaintext msg = new Plaintext.Builder(MSG)
                        .from(from)
                        .to(to)
                        .message(subject, message)
                        .labels(messages().getLabels(Label.Type.SENT))
                        .build();
                if (to.getPubkey() == null) {
                    tryToFindMatchingPubkey(to);
                }
                if (to.getPubkey() == null) {
                    LOG.info("Public key is missing from recipient. Requesting.");
                    requestPubkey(from, to);
                    msg.setStatus(PUBKEY_REQUESTED);
                    ctx.getMessageRepository().save(msg);
                } else {
                    LOG.info("Sending message.");
                    msg.setStatus(DOING_PROOF_OF_WORK);
                    ctx.getMessageRepository().save(msg);
                    ctx.send(
                            from,
                            to,
                            new Msg(msg),
                            +2 * DAY
                    );
                    msg.setStatus(SENT);
                    msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.SENT));
                    ctx.getMessageRepository().save(msg);
                }
            }
        });
    }

    public void send(final Plaintext msg) {
        if (msg.getFrom() == null || msg.getFrom().getPrivateKey() == null) {
            throw new IllegalArgumentException("'From' must be an identity, i.e. have a private key.");
        }
        pool.submit(new Runnable() {
            @Override
            public void run() {
                BitmessageAddress to = msg.getTo();
                if (to.getPubkey() == null) {
                    tryToFindMatchingPubkey(to);
                }
                if (to.getPubkey() == null) {
                    LOG.info("Public key is missing from recipient. Requesting.");
                    requestPubkey(msg.getFrom(), to);
                    msg.setStatus(PUBKEY_REQUESTED);
                    ctx.getMessageRepository().save(msg);
                } else {
                    LOG.info("Sending message.");
                    msg.setStatus(DOING_PROOF_OF_WORK);
                    ctx.getMessageRepository().save(msg);
                    ctx.send(
                            msg.getFrom(),
                            to,
                            new Msg(msg),
                            +2 * DAY
                    );
                    msg.setStatus(SENT);
                    msg.addLabels(ctx.getMessageRepository().getLabels(Label.Type.SENT));
                    ctx.getMessageRepository().save(msg);
                }
            }
        });
    }

    private void requestPubkey(BitmessageAddress requestingIdentity, BitmessageAddress address) {
        ctx.send(
                requestingIdentity,
                address,
                new GetPubkey(address),
                +28 * DAY
        );
    }

    public void startup() {
        ctx.getNetworkHandler().start(networkListener);
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
        Future<?> future = ctx.getNetworkHandler().synchronize(host, port, networkListener, timeoutInSeconds);
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

    public void cleanup() {
        ctx.getInventory().cleanup();
    }

    public boolean isRunning() {
        return ctx.getNetworkHandler().isRunning();
    }

    public void addContact(BitmessageAddress contact) {
        ctx.getAddressRepository().save(contact);
        tryToFindMatchingPubkey(contact);
        if (contact.getPubkey() == null) {
            ctx.requestPubkey(contact);
        }
    }

    private void tryToFindMatchingPubkey(BitmessageAddress address) {
        for (ObjectMessage object : ctx.getInventory().getObjects(address.getStream(), address.getVersion(), ObjectType.PUBKEY)) {
            try {
                Pubkey pubkey = (Pubkey) object.getPayload();
                if (address.getVersion() == 4) {
                    V4Pubkey v4Pubkey = (V4Pubkey) pubkey;
                    if (Arrays.equals(address.getTag(), v4Pubkey.getTag())) {
                        v4Pubkey.decrypt(address.getPublicDecryptionKey());
                        if (object.isSignatureValid(v4Pubkey)) {
                            address.setPubkey(v4Pubkey);
                            ctx.getAddressRepository().save(address);
                            break;
                        } else {
                            LOG.info("Found pubkey for " + address + " but signature is invalid");
                        }
                    }
                } else {
                    if (Arrays.equals(pubkey.getRipe(), address.getRipe())) {
                        address.setPubkey(pubkey);
                        ctx.getAddressRepository().save(address);
                        break;
                    }
                }
            } catch (Exception e) {
                LOG.debug(e.getMessage(), e);
            }
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
                listener.receive(broadcast.getPlaintext());
            } catch (DecryptionFailedException ignore) {
            } catch (Exception e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

    public Property status() {
        return new Property("status", null,
                ctx.getNetworkHandler().getNetworkStatus()
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
        MessageCallback messageCallback;
        CustomCommandHandler customCommandHandler;
        Listener listener;
        int connectionLimit = 150;
        long connectionTTL = 30 * MINUTE;
        boolean sendPubkeyOnIdentityCreation = true;
        long pubkeyTTL = 28;

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

        public Builder powRepo(ProofOfWorkRepository proofOfWorkRepository) {
            this.proofOfWorkRepository = proofOfWorkRepository;
            return this;
        }

        public Builder cryptography(Cryptography cryptography) {
            this.cryptography = cryptography;
            return this;
        }

        public Builder messageCallback(MessageCallback callback) {
            this.messageCallback = callback;
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
         */
        public Builder pubkeyTTL(long days) {
            if (days < 0 || days > 28 * DAY) throw new IllegalArgumentException("TTL must be between 1 and 28 days");
            this.pubkeyTTL = days;
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
            if (messageCallback == null) {
                messageCallback = new MessageCallback() {
                    @Override
                    public void proofOfWorkStarted(ObjectPayload message) {
                    }

                    @Override
                    public void proofOfWorkCompleted(ObjectPayload message) {
                    }

                    @Override
                    public void messageOffered(ObjectPayload message, InventoryVector iv) {
                    }

                    @Override
                    public void messageAcknowledged(InventoryVector iv) {
                    }
                };
            }
            if (customCommandHandler == null) {
                customCommandHandler = new CustomCommandHandler() {
                    @Override
                    public MessagePayload handle(CustomMessage request) {
                        throw new RuntimeException("Received custom request, but no custom command handler configured.");
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
