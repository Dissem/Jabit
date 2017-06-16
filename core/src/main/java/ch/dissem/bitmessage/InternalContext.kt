/*
 * Copyright 2017 Christian Basler
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

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Encrypted
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.payload.*
import ch.dissem.bitmessage.ports.*
import ch.dissem.bitmessage.utils.Singleton
import ch.dissem.bitmessage.utils.TTL
import ch.dissem.bitmessage.utils.UnixTime
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.KProperty

/**
 * The internal context should normally only be used for port implementations. If you need it in your client
 * implementation, you're either doing something wrong, something very weird, or the BitmessageContext should
 * get extended.
 *
 *
 * On the other hand, if you need the BitmessageContext in a port implementation, the same thing might apply.
 *
 */
class InternalContext(
    val cryptography: Cryptography,
    val inventory: ch.dissem.bitmessage.ports.Inventory,
    val nodeRegistry: NodeRegistry,
    val networkHandler: NetworkHandler,
    val addressRepository: AddressRepository,
    val messageRepository: ch.dissem.bitmessage.ports.MessageRepository,
    val proofOfWorkRepository: ProofOfWorkRepository,
    val proofOfWorkEngine: ProofOfWorkEngine,
    val customCommandHandler: CustomCommandHandler,
    listener: BitmessageContext.Listener,
    val labeler: Labeler,

    val port: Int,
    val connectionTTL: Long,
    val connectionLimit: Int
) {

    private val threadPool = Executors.newCachedThreadPool()

    val proofOfWorkService: ProofOfWorkService = ProofOfWorkService()
    val networkListener: NetworkHandler.MessageListener = DefaultMessageListener(labeler, listener)
    val clientNonce: Long = cryptography.randomNonce()
    private val _streams = TreeSet<Long>()
    val streams: LongArray
        get() = _streams.toLongArray()

    init {
        lateinit.instance = this
        lateinit = ContextDelegate()
        Singleton.initialize(cryptography)

        // TODO: streams of new identities and subscriptions should also be added. This works only after a restart.
        addressRepository.getIdentities().mapTo(_streams) { it.stream }
        addressRepository.getSubscriptions().mapTo(_streams) { it.stream }
        if (_streams.isEmpty()) {
            _streams.add(1L)
        }

        init(cryptography, inventory, nodeRegistry, networkHandler, addressRepository, messageRepository,
            proofOfWorkRepository, proofOfWorkService, proofOfWorkEngine, customCommandHandler, labeler,
            networkListener)
    }

    private fun init(vararg objects: Any) {
        objects.filter { it is ContextHolder }.forEach { (it as ContextHolder).setContext(this) }
    }

    fun send(plaintext: Plaintext) {
        if (plaintext.ackMessage != null) {
            val expires = UnixTime.now + plaintext.ttl
            LOG.info("Expires at " + expires)
            proofOfWorkService.doProofOfWorkWithAck(plaintext, expires)
        } else {
            send(plaintext.from, plaintext.to, Msg(plaintext), plaintext.ttl)
        }
    }

    fun send(from: BitmessageAddress, to: BitmessageAddress?, payload: ObjectPayload,
             timeToLive: Long) {
        val recipient = to ?: from
        val expires = UnixTime.now + timeToLive
        LOG.info("Expires at " + expires)
        val objectMessage = ObjectMessage(
            stream = recipient.stream,
            expiresTime = expires,
            payload = payload
        )
        if (objectMessage.isSigned) {
            objectMessage.sign(
                from.privateKey ?: throw IllegalArgumentException("The given sending address is no identity")
            )
        }
        if (payload is Broadcast) {
            payload.encrypt()
        } else if (payload is Encrypted) {
            objectMessage.encrypt(
                recipient.pubkey ?: throw IllegalArgumentException("The public key for the recipient isn't available")
            )
        }
        proofOfWorkService.doProofOfWork(to, objectMessage)
    }

    fun sendPubkey(identity: BitmessageAddress, targetStream: Long) {
        val expires = UnixTime.now + TTL.pubkey
        LOG.info("Expires at " + expires)
        val payload = identity.pubkey ?: throw IllegalArgumentException("The given address is no identity")
        val response = ObjectMessage(
            expiresTime = expires,
            stream = targetStream,
            payload = payload
        )
        response.sign(
            identity.privateKey ?: throw IllegalArgumentException("The given address is no identity")
        )
        response.encrypt(cryptography.createPublicKey(identity.publicDecryptionKey))
        // TODO: remember that the pubkey is just about to be sent, and on which stream!
        proofOfWorkService.doProofOfWork(response)
    }

    /**
     * Be aware that if the pubkey already exists in the inventory, the metods will not request it and the callback
     * for freshly received pubkeys will not be called. Instead the pubkey is added to the contact and stored on DB.
     */
    fun requestPubkey(contact: BitmessageAddress) {
        threadPool.execute {
            val stored = addressRepository.getAddress(contact.address)

            tryToFindMatchingPubkey(contact)
            if (contact.pubkey != null) {
                if (stored != null) {
                    stored.pubkey = contact.pubkey
                    addressRepository.save(stored)
                } else {
                    addressRepository.save(contact)
                }
                return@execute
            }

            if (stored == null) {
                addressRepository.save(contact)
            }

            val expires = UnixTime.now + TTL.getpubkey
            LOG.info("Expires at $expires")
            val request = ObjectMessage(
                stream = contact.stream,
                expiresTime = expires,
                payload = GetPubkey(contact)
            )
            proofOfWorkService.doProofOfWork(request)
        }
    }

    private fun tryToFindMatchingPubkey(address: BitmessageAddress) {
        addressRepository.getAddress(address.address)?.let {
            address.alias = it.alias
            address.isSubscribed = it.isSubscribed
        }
        for (objectMessage in inventory.getObjects(address.stream, address.version, ObjectType.PUBKEY)) {
            try {
                val pubkey = objectMessage.payload as Pubkey
                if (address.version == 4L) {
                    val v4Pubkey = pubkey as V4Pubkey
                    if (Arrays.equals(address.tag, v4Pubkey.tag)) {
                        v4Pubkey.decrypt(address.publicDecryptionKey)
                        if (objectMessage.isSignatureValid(v4Pubkey)) {
                            address.pubkey = v4Pubkey
                            addressRepository.save(address)
                            break
                        } else {
                            LOG.info("Found pubkey for $address but signature is invalid")
                        }
                    }
                } else {
                    if (Arrays.equals(pubkey.ripe, address.ripe)) {
                        address.pubkey = pubkey
                        addressRepository.save(address)
                        break
                    }
                }
            } catch (e: Exception) {
                LOG.debug(e.message, e)
            }
        }
    }

    fun resendUnacknowledged() {
        val messages = messageRepository.findMessagesToResend()
        for (message in messages) {
            send(message)
            messageRepository.save(message)
        }
    }

    interface ContextHolder {
        fun setContext(context: InternalContext)
    }

    class ContextDelegate {
        internal lateinit var instance: InternalContext
        operator fun getValue(thisRef: Any?, property: KProperty<*>) = instance
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: InternalContext) {}
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(InternalContext::class.java)

        @JvmField val NETWORK_NONCE_TRIALS_PER_BYTE: Long = 1000
        @JvmField val NETWORK_EXTRA_BYTES: Long = 1000

        var lateinit = ContextDelegate()
            private set
    }
}
