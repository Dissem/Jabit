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
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Status.PUBKEY_REQUESTED
import ch.dissem.bitmessage.entity.payload.*
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.ports.Labeler
import ch.dissem.bitmessage.ports.NetworkHandler
import ch.dissem.bitmessage.utils.Strings.hex
import org.slf4j.LoggerFactory
import java.util.*

internal open class DefaultMessageListener(
    private val labeler: Labeler,
    private val listener: BitmessageContext.Listener
) : NetworkHandler.MessageListener {
    private var ctx by InternalContext.lateinit

    override fun receive(objectMessage: ObjectMessage) {
        val payload = objectMessage.payload

        when (payload.type) {
            ObjectType.GET_PUBKEY -> {
                receive(objectMessage, payload as GetPubkey)
            }
            ObjectType.PUBKEY -> {
                receive(objectMessage, payload as Pubkey)
            }
            ObjectType.MSG -> {
                receive(objectMessage, payload as Msg)
            }
            ObjectType.BROADCAST -> {
                receive(objectMessage, payload as Broadcast)
            }
            null -> {
                if (payload is GenericPayload) {
                    receive(payload)
                }
            }
            else -> {
                throw IllegalArgumentException("Unknown payload type " + payload.type!!)
            }
        }
    }

    protected fun receive(objectMessage: ObjectMessage, getPubkey: GetPubkey) {
        val identity = ctx.addressRepository.findIdentity(getPubkey.ripeTag)
        if (identity != null && identity.privateKey != null && !identity.isChan) {
            LOG.info("Got pubkey request for identity " + identity)
            // FIXME: only send pubkey if it wasn't sent in the last TTL.pubkey() days
            ctx.sendPubkey(identity, objectMessage.stream)
        }
    }

    protected fun receive(objectMessage: ObjectMessage, pubkey: Pubkey) {
        try {
            if (pubkey is V4Pubkey) {
                ctx.addressRepository.findContact(pubkey.tag)?.let {
                    if (it.pubkey == null) {
                        pubkey.decrypt(it.publicDecryptionKey)
                        updatePubkey(it, pubkey)
                    }
                }
            } else {
                ctx.addressRepository.findContact(pubkey.ripe)?.let {
                    if (it.pubkey == null) {
                        updatePubkey(it, pubkey)
                    }
                }
            }
        } catch (_: DecryptionFailedException) {
        }

    }

    private fun updatePubkey(address: BitmessageAddress, pubkey: Pubkey) {
        address.pubkey = pubkey
        LOG.info("Got pubkey for contact " + address)
        ctx.addressRepository.save(address)
        val messages = ctx.messageRepository.findMessages(PUBKEY_REQUESTED, address)
        LOG.info("Sending " + messages.size + " messages for contact " + address)
        for (msg in messages) {
            ctx.labeler.markAsSending(msg)
            ctx.messageRepository.save(msg)
            ctx.send(msg)
        }
    }

    protected fun receive(objectMessage: ObjectMessage, msg: Msg) {
        for (identity in ctx.addressRepository.getIdentities()) {
            try {
                msg.decrypt(identity.privateKey!!.privateEncryptionKey)
                val plaintext = msg.plaintext!!
                plaintext.to = identity
                if (!objectMessage.isSignatureValid(plaintext.from.pubkey!!)) {
                    LOG.warn("Msg with IV " + objectMessage.inventoryVector + " was successfully decrypted, but signature check failed. Ignoring.")
                } else {
                    receive(objectMessage.inventoryVector, plaintext)
                }
                break
            } catch (_: DecryptionFailedException) {
            }
        }
    }

    protected fun receive(ack: GenericPayload) {
        if (ack.data.size == Msg.ACK_LENGTH) {
            ctx.messageRepository.getMessageForAck(ack.data)?.let {
                ctx.labeler.markAsAcknowledged(it)
                ctx.messageRepository.save(it)
            } ?: LOG.debug("Message not found for ack ${hex(ack.data)}")
        }
    }

    protected fun receive(objectMessage: ObjectMessage, broadcast: Broadcast) {
        val tag = if (broadcast is V5Broadcast) broadcast.tag else null
        for (subscription in ctx.addressRepository.getSubscriptions(broadcast.version)) {
            if (tag != null && !Arrays.equals(tag, subscription.tag)) {
                continue
            }
            try {
                broadcast.decrypt(subscription.publicDecryptionKey)
                if (!objectMessage.isSignatureValid(broadcast.plaintext!!.from.pubkey!!)) {
                    LOG.warn("Broadcast with IV " + objectMessage.inventoryVector + " was successfully decrypted, but signature check failed. Ignoring.")
                } else {
                    receive(objectMessage.inventoryVector, broadcast.plaintext!!)
                }
            } catch (_: DecryptionFailedException) {
            }
        }
    }

    protected fun receive(iv: InventoryVector, msg: Plaintext) {
        val contact = ctx.addressRepository.getAddress(msg.from.address)
        if (contact != null && contact.pubkey == null) {
            updatePubkey(contact, msg.from.pubkey!!)
        }

        msg.inventoryVector = iv
        labeler.setLabels(msg)
        ctx.messageRepository.save(msg)
        listener.receive(msg)

        if (msg.type == Plaintext.Type.MSG && msg.to!!.has(Pubkey.Feature.DOES_ACK)) {
            msg.ackMessage?.let {
                ctx.inventory.storeObject(it)
                ctx.networkHandler.offer(it.inventoryVector)
            } ?: LOG.debug("ack message expected")
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DefaultMessageListener::class.java)
    }
}
