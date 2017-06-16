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

import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.*
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.ports.ProofOfWorkEngine
import ch.dissem.bitmessage.ports.ProofOfWorkRepository.Item
import ch.dissem.bitmessage.utils.Strings
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/**
 * @author Christian Basler
 */
class ProofOfWorkService : ProofOfWorkEngine.Callback {

    private val ctx by InternalContext.lateinit
    private val cryptography by lazy { ctx.cryptography }
    private val powRepo by lazy { ctx.proofOfWorkRepository }
    private val messageRepo by lazy { ctx.messageRepository }

    fun doMissingProofOfWork(delayInMilliseconds: Long) {
        val items = powRepo.getItems()
        if (items.isEmpty()) return

        // Wait for 30 seconds, to let the application start up before putting heavy load on the CPU
        Timer().schedule(object : TimerTask() {
            override fun run() {
                LOG.info("Doing POW for " + items.size + " tasks.")
                for (initialHash in items) {
                    val (objectMessage, nonceTrialsPerByte, extraBytes) = powRepo.getItem(initialHash)
                    cryptography.doProofOfWork(objectMessage, nonceTrialsPerByte, extraBytes,
                        this@ProofOfWorkService)
                }
            }
        }, delayInMilliseconds)
    }

    fun doProofOfWork(objectMessage: ObjectMessage) {
        doProofOfWork(null, objectMessage)
    }

    fun doProofOfWork(recipient: BitmessageAddress?, objectMessage: ObjectMessage) {
        val pubkey = recipient?.pubkey

        val nonceTrialsPerByte = pubkey?.nonceTrialsPerByte ?: NETWORK_NONCE_TRIALS_PER_BYTE
        val extraBytes = pubkey?.extraBytes ?: NETWORK_EXTRA_BYTES

        powRepo.putObject(objectMessage, nonceTrialsPerByte, extraBytes)
        if (objectMessage.payload is PlaintextHolder) {
            objectMessage.payload.plaintext?.let {
                it.initialHash = cryptography.getInitialHash(objectMessage)
                messageRepo.save(it)
            } ?: LOG.error("PlaintextHolder without Plaintext shouldn't make it to the POW")
        }
        cryptography.doProofOfWork(objectMessage, nonceTrialsPerByte, extraBytes, this)
    }

    fun doProofOfWorkWithAck(plaintext: Plaintext, expirationTime: Long) {
        val ack = plaintext.ackMessage!!
        messageRepo.save(plaintext)
        val item = Item(ack, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES,
            expirationTime, plaintext)
        powRepo.putObject(item)
        cryptography.doProofOfWork(ack, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES, this)
    }

    override fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray) {
        val (objectMessage, _, _, expirationTime, message) = powRepo.getItem(initialHash)
        if (message == null) {
            objectMessage.nonce = nonce
            messageRepo.getMessage(initialHash)?.let {
                it.inventoryVector = objectMessage.inventoryVector
                it.updateNextTry()
                ctx.labeler.markAsSent(it)
                messageRepo.save(it)
            }
            ctx.inventory.storeObject(objectMessage)
            ctx.networkHandler.offer(objectMessage.inventoryVector)
        } else {
            message.ackMessage!!.nonce = nonce
            val newObjectMessage = ObjectMessage.Builder()
                .stream(message.stream)
                .expiresTime(expirationTime!!)
                .payload(Msg(message))
                .build()
            if (newObjectMessage.isSigned) {
                newObjectMessage.sign(message.from.privateKey!!)
            }
            if (newObjectMessage.payload is Encrypted) {
                newObjectMessage.encrypt(message.to!!.pubkey!!)
            }
            doProofOfWork(message.to, newObjectMessage)
        }
        powRepo.removeObject(initialHash)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ProofOfWorkService::class.java)
    }
}
