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

package ch.dissem.bitmessage

import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.CustomMessage
import ch.dissem.bitmessage.entity.MessagePayload
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Status.DRAFT
import ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.Broadcast
import ch.dissem.bitmessage.entity.payload.ObjectType
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.*
import ch.dissem.bitmessage.utils.Property
import ch.dissem.bitmessage.utils.UnixTime.HOUR
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlin.properties.Delegates

/**
 *
 * Use this class if you want to create a Bitmessage client.
 * You'll need the Builder to create a BitmessageContext, and set the following properties:
 *
 *  * addressRepo
 *  * inventory
 *  * nodeRegistry
 *  * networkHandler
 *  * messageRepo
 *  * streams
 *
 *
 * The default implementations in the different module builds can be used.
 *
 * The port defaults to 8444 (the default Bitmessage port)
 */
class BitmessageContext(
    cryptography: Cryptography,
    inventory: Inventory,
    nodeRegistry: NodeRegistry,
    networkHandler: NetworkHandler,
    addressRepository: AddressRepository,
    messageRepository: MessageRepository,
    proofOfWorkRepository: ProofOfWorkRepository,
    proofOfWorkEngine: ProofOfWorkEngine = MultiThreadedPOWEngine(),
    customCommandHandler: CustomCommandHandler = object : CustomCommandHandler {
        override fun handle(request: CustomMessage): MessagePayload? {
            BitmessageContext.LOG.debug("Received custom request, but no custom command handler configured.")
            return null
        }
    },
    listener: Listener,
    labeler: Labeler = DefaultLabeler(),
    port: Int = 8444,
    connectionTTL: Long = 30 * MINUTE,
    connectionLimit: Int = 150,
    sendPubkeyOnIdentityCreation: Boolean,
    doMissingProofOfWorkDelayInSeconds: Int = 30
) {

    private constructor(builder: BitmessageContext.Builder) : this(
        builder.cryptography,
        builder.inventory,
        builder.nodeRegistry,
        builder.networkHandler,
        builder.addressRepo,
        builder.messageRepo,
        builder.proofOfWorkRepository,
        builder.proofOfWorkEngine ?: MultiThreadedPOWEngine(),
        builder.customCommandHandler ?: object : CustomCommandHandler {
            override fun handle(request: CustomMessage): MessagePayload? {
                BitmessageContext.LOG.debug("Received custom request, but no custom command handler configured.")
                return null
            }
        },
        builder.listener,
        builder.labeler ?: DefaultLabeler(),
        builder.port,
        builder.connectionTTL,
        builder.connectionLimit,
        builder.sendPubkeyOnIdentityCreation,
        builder.doMissingProofOfWorkDelay
    )

    private val sendPubkeyOnIdentityCreation: Boolean

    /**
     * The [InternalContext] - normally you wouldn't need it,
     * unless you are doing something crazy with the protocol.
     */
    val internals: InternalContext
        @JvmName("internals") get

    val labeler: Labeler
        @JvmName("labeler") get

    val addresses: AddressRepository
        @JvmName("addresses") get

    val messages: MessageRepository
        @JvmName("messages") get

    fun createIdentity(shorter: Boolean, vararg features: Feature): BitmessageAddress {
        val identity = BitmessageAddress(PrivateKey(
            shorter,
            internals.streams[0],
            NETWORK_NONCE_TRIALS_PER_BYTE,
            NETWORK_EXTRA_BYTES,
            *features
        ))
        internals.addressRepository.save(identity)
        if (sendPubkeyOnIdentityCreation) {
            internals.sendPubkey(identity, identity.stream)
        }
        return identity
    }

    fun joinChan(passphrase: String, address: String): BitmessageAddress {
        val chan = BitmessageAddress.chan(address, passphrase)
        chan.alias = passphrase
        internals.addressRepository.save(chan)
        return chan
    }

    fun createChan(passphrase: String): BitmessageAddress {
        // FIXME: hardcoded stream number
        val chan = BitmessageAddress.chan(1, passphrase)
        internals.addressRepository.save(chan)
        return chan
    }

    fun createDeterministicAddresses(
        passphrase: String, numberOfAddresses: Int, version: Long, stream: Long, shorter: Boolean): List<BitmessageAddress> {
        val result = BitmessageAddress.deterministic(
            passphrase, numberOfAddresses, version, stream, shorter)
        for (i in result.indices) {
            val address = result[i]
            address.alias = "deterministic (" + (i + 1) + ")"
            internals.addressRepository.save(address)
        }
        return result
    }

    fun broadcast(from: BitmessageAddress, subject: String, message: String) {
        send(Plaintext(
            type = BROADCAST,
            from = from,
            subject = subject,
            body = message,
            status = DRAFT
        ))
    }

    fun send(from: BitmessageAddress, to: BitmessageAddress, subject: String, message: String) {
        if (from.privateKey == null) {
            throw IllegalArgumentException("'From' must be an identity, i.e. have a private key.")
        }
        send(Plaintext(
            type = MSG,
            from = from,
            to = to,
            subject = subject,
            body = message
        ))
    }

    fun send(msg: Plaintext) {
        if (msg.from.privateKey == null) {
            throw IllegalArgumentException("'From' must be an identity, i.e. have a private key.")
        }
        labeler.markAsSending(msg)
        val to = msg.to
        if (to != null) {
            if (to.pubkey == null) {
                LOG.info("Public key is missing from recipient. Requesting.")
                internals.requestPubkey(to)
            }
            if (to.pubkey == null) {
                internals.messageRepository.save(msg)
            }
        }
        if (to == null || to.pubkey != null) {
            LOG.info("Sending message.")
            internals.messageRepository.save(msg)
            if (msg.type == MSG) {
                internals.send(msg)
            } else {
                internals.send(
                    msg.from,
                    to,
                    Factory.getBroadcast(msg),
                    msg.ttl
                )
            }
        }
    }

    fun startup() {
        internals.networkHandler.start()
    }

    fun shutdown() {
        internals.networkHandler.stop()
    }

    /**
     * @param host             a trusted node that must be reliable (it's used for every synchronization)
     * *
     * @param port             of the trusted host, default is 8444
     * *
     * @param timeoutInSeconds synchronization should end no later than about 5 seconds after the timeout elapsed, even
     * *                         if not all objects were fetched
     * *
     * @param wait             waits for the synchronization thread to finish
     */
    fun synchronize(host: InetAddress, port: Int, timeoutInSeconds: Long, wait: Boolean) {
        val future = internals.networkHandler.synchronize(host, port, timeoutInSeconds)
        if (wait) {
            try {
                future.get()
            } catch (e: InterruptedException) {
                LOG.info("Thread was interrupted. Trying to shut down synchronization and returning.")
                future.cancel(true)
            } catch (e: CancellationException) {
                LOG.debug(e.message, e)
            } catch (e: ExecutionException) {
                LOG.debug(e.message, e)
            }

        }
    }

    /**
     * Send a custom message to a specific node (that should implement handling for this message type) and returns
     * the response, which in turn is expected to be a [CustomMessage].

     * @param server  the node's address
     * *
     * @param port    the node's port
     * *
     * @param request the request
     * *
     * @return the response
     */
    fun send(server: InetAddress, port: Int, request: CustomMessage): CustomMessage {
        return internals.networkHandler.send(server, port, request)
    }

    /**
     * Removes expired objects from the inventory. You should call this method regularly,
     * e.g. daily and on each shutdown.
     */
    fun cleanup() {
        internals.inventory.cleanup()
    }

    /**
     * Sends messages again whose time to live expired without being acknowledged. (And whose
     * recipient is expected to send acknowledgements.
     *
     *
     * You should call this method regularly, but be aware of the following:
     *
     *  * As messages might be sent, POW will be done. It is therefore not advised to
     * call it on shutdown.
     *  * It shouldn't be called right after startup, as it's possible the missing
     * acknowledgement was sent while the client was offline.
     *  * Other than that, the call isn't expensive as long as there is no message
     * to send, so it might be a good idea to just call it every few minutes.
     *
     */
    fun resendUnacknowledgedMessages() {
        internals.resendUnacknowledged()
    }

    val isRunning: Boolean
        get() = internals.networkHandler.isRunning

    fun addContact(contact: BitmessageAddress) {
        internals.addressRepository.save(contact)
        if (contact.pubkey == null) {
            // If it already existed, the saved contact might have the public key
            if (internals.addressRepository.getAddress(contact.address)!!.pubkey == null) {
                internals.requestPubkey(contact)
            }
        }
    }

    fun addSubscribtion(address: BitmessageAddress) {
        address.isSubscribed = true
        internals.addressRepository.save(address)
        tryToFindBroadcastsForAddress(address)
    }

    private fun tryToFindBroadcastsForAddress(address: BitmessageAddress) {
        for (objectMessage in internals.inventory.getObjects(address.stream, Broadcast.getVersion(address), ObjectType.BROADCAST)) {
            try {
                val broadcast = objectMessage.payload as Broadcast
                broadcast.decrypt(address)
                // This decrypts it twice, but on the other hand it doesn't try to decrypt the objects with
                // other subscriptions and the interface stays as simple as possible.
                internals.networkListener.receive(objectMessage)
            } catch (ignore: DecryptionFailedException) {
            } catch (e: Exception) {
                LOG.debug(e.message, e)
            }
        }
    }

    fun status(): Property {
        return Property("status",
            internals.networkHandler.networkStatus,
            Property("unacknowledged", internals.messageRepository.findMessagesToResend().size)
        )
    }

    interface Listener {
        fun receive(plaintext: Plaintext)

        /**
         * A message listener that needs a [BitmessageContext], i.e. for implementing some sort of chat bot.
         */
        interface WithContext : Listener {
            fun setContext(ctx: BitmessageContext)
        }
    }

    class Builder {
        internal var port = 8444
        internal var inventory by Delegates.notNull<Inventory>()
        internal var nodeRegistry by Delegates.notNull<NodeRegistry>()
        internal var networkHandler by Delegates.notNull<NetworkHandler>()
        internal var addressRepo by Delegates.notNull<AddressRepository>()
        internal var messageRepo by Delegates.notNull<MessageRepository>()
        internal var proofOfWorkRepository by Delegates.notNull<ProofOfWorkRepository>()
        internal var proofOfWorkEngine: ProofOfWorkEngine? = null
        internal var cryptography by Delegates.notNull<Cryptography>()
        internal var customCommandHandler: CustomCommandHandler? = null
        internal var labeler: Labeler? = null
        internal var listener by Delegates.notNull<Listener>()
        internal var connectionLimit = 150
        internal var connectionTTL = 30 * MINUTE
        internal var sendPubkeyOnIdentityCreation = true
        internal var doMissingProofOfWorkDelay = 30

        fun port(port: Int): Builder {
            this.port = port
            return this
        }

        fun inventory(inventory: Inventory): Builder {
            this.inventory = inventory
            return this
        }

        fun nodeRegistry(nodeRegistry: NodeRegistry): Builder {
            this.nodeRegistry = nodeRegistry
            return this
        }

        fun networkHandler(networkHandler: NetworkHandler): Builder {
            this.networkHandler = networkHandler
            return this
        }

        fun addressRepo(addressRepo: AddressRepository): Builder {
            this.addressRepo = addressRepo
            return this
        }

        fun messageRepo(messageRepo: MessageRepository): Builder {
            this.messageRepo = messageRepo
            return this
        }

        fun powRepo(proofOfWorkRepository: ProofOfWorkRepository): Builder {
            this.proofOfWorkRepository = proofOfWorkRepository
            return this
        }

        fun cryptography(cryptography: Cryptography): Builder {
            this.cryptography = cryptography
            return this
        }

        fun customCommandHandler(handler: CustomCommandHandler): Builder {
            this.customCommandHandler = handler
            return this
        }

        fun proofOfWorkEngine(proofOfWorkEngine: ProofOfWorkEngine): Builder {
            this.proofOfWorkEngine = proofOfWorkEngine
            return this
        }

        fun labeler(labeler: Labeler): Builder {
            this.labeler = labeler
            return this
        }

        fun listener(listener: Listener): Builder {
            this.listener = listener
            return this
        }

        @JvmName("kotlinListener")
        fun listener(listener: (Plaintext) -> Unit): Builder {
            this.listener = object : Listener {
                override fun receive(plaintext: Plaintext) {
                    listener.invoke(plaintext)
                }
            }
            return this
        }

        fun connectionLimit(connectionLimit: Int): Builder {
            this.connectionLimit = connectionLimit
            return this
        }

        fun connectionTTL(hours: Int): Builder {
            this.connectionTTL = hours * HOUR
            return this
        }

        fun doMissingProofOfWorkDelay(seconds: Int) {
            this.doMissingProofOfWorkDelay = seconds
        }

        /**
         * By default a client will send the public key when an identity is being created. On weaker devices
         * this behaviour might not be desirable.
         */
        fun doNotSendPubkeyOnIdentityCreation(): Builder {
            this.sendPubkeyOnIdentityCreation = false
            return this
        }

        fun build(): BitmessageContext {
            return BitmessageContext(this)
        }
    }


    init {
        this.labeler = labeler
        this.internals = InternalContext(
            cryptography,
            inventory,
            nodeRegistry,
            networkHandler,
            addressRepository,
            messageRepository,
            proofOfWorkRepository,
            proofOfWorkEngine,
            customCommandHandler,
            listener,
            labeler,
            port,
            connectionTTL,
            connectionLimit
        )
        this.addresses = addressRepository
        this.messages = messageRepository
        this.sendPubkeyOnIdentityCreation = sendPubkeyOnIdentityCreation
        (listener as? Listener.WithContext)?.setContext(this)
        internals.proofOfWorkService.doMissingProofOfWork(doMissingProofOfWorkDelayInSeconds * 1000L)
    }

    companion object {
        @JvmField val CURRENT_VERSION = 3
        private val LOG = LoggerFactory.getLogger(BitmessageContext::class.java)
    }
}
