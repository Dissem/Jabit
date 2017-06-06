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
import org.slf4j.LoggerFactory
import java.util.*

internal open class DefaultMessageListener(
    private val labeler: Labeler,
    private val listener: BitmessageContext.Listener
) : NetworkHandler.MessageListener {
    private var ctx by InternalContext

    override fun receive(`object`: ObjectMessage) {
        val payload = `object`.payload

        when (payload.type) {
            ObjectType.GET_PUBKEY -> {
                receive(`object`, payload as GetPubkey)
            }
            ObjectType.PUBKEY -> {
                receive(`object`, payload as Pubkey)
            }
            ObjectType.MSG -> {
                receive(`object`, payload as Msg)
            }
            ObjectType.BROADCAST -> {
                receive(`object`, payload as Broadcast)
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

    protected fun receive(`object`: ObjectMessage, getPubkey: GetPubkey) {
        val identity = ctx.addressRepository.findIdentity(getPubkey.ripeTag)
        if (identity != null && identity.privateKey != null && !identity.isChan) {
            LOG.info("Got pubkey request for identity " + identity)
            // FIXME: only send pubkey if it wasn't sent in the last TTL.pubkey() days
            ctx.sendPubkey(identity, `object`.stream)
        }
    }

    protected fun receive(`object`: ObjectMessage, pubkey: Pubkey) {
        val address: BitmessageAddress?
        try {
            if (pubkey is V4Pubkey) {
                address = ctx.addressRepository.findContact(pubkey.tag)
                if (address != null) {
                    pubkey.decrypt(address.publicDecryptionKey)
                }
            } else {
                address = ctx.addressRepository.findContact(pubkey.ripe)
            }
            if (address != null && address.pubkey == null) {
                updatePubkey(address, pubkey)
            }
        } catch (_: DecryptionFailedException) {}

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

    protected fun receive(`object`: ObjectMessage, msg: Msg) {
        for (identity in ctx.addressRepository.getIdentities()) {
            try {
                msg.decrypt(identity.privateKey!!.privateEncryptionKey)
                val plaintext = msg.plaintext!!
                plaintext.to = identity
                if (!`object`.isSignatureValid(plaintext.from.pubkey!!)) {
                    LOG.warn("Msg with IV " + `object`.inventoryVector + " was successfully decrypted, but signature check failed. Ignoring.")
                } else {
                    receive(`object`.inventoryVector, plaintext)
                }
                break
            } catch (_: DecryptionFailedException) {}
        }
    }

    protected fun receive(ack: GenericPayload) {
        if (ack.data.size == Msg.ACK_LENGTH) {
            ctx.messageRepository.getMessageForAck(ack.data)?.let {
                ctx.labeler.markAsAcknowledged(it)
                ctx.messageRepository.save(it)
            }
        }
    }

    protected fun receive(`object`: ObjectMessage, broadcast: Broadcast) {
        val tag = if (broadcast is V5Broadcast) broadcast.tag else null
        for (subscription in ctx.addressRepository.getSubscriptions(broadcast.version)) {
            if (tag != null && !Arrays.equals(tag, subscription.tag)) {
                continue
            }
            try {
                broadcast.decrypt(subscription.publicDecryptionKey)
                if (!`object`.isSignatureValid(broadcast.plaintext!!.from.pubkey!!)) {
                    LOG.warn("Broadcast with IV " + `object`.inventoryVector + " was successfully decrypted, but signature check failed. Ignoring.")
                } else {
                    receive(`object`.inventoryVector, broadcast.plaintext!!)
                }
            } catch (_: DecryptionFailedException) {}
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
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DefaultMessageListener::class.java)
    }
}
