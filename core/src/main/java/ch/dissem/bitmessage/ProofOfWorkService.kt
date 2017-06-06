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
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/**
 * @author Christian Basler
 */
class ProofOfWorkService : ProofOfWorkEngine.Callback {

    private val ctx by InternalContext
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
                    val (`object`, nonceTrialsPerByte, extraBytes) = powRepo.getItem(initialHash)
                    cryptography.doProofOfWork(`object`, nonceTrialsPerByte, extraBytes,
                        this@ProofOfWorkService)
                }
            }
        }, delayInMilliseconds)
    }

    fun doProofOfWork(`object`: ObjectMessage) {
        doProofOfWork(null, `object`)
    }

    fun doProofOfWork(recipient: BitmessageAddress?, `object`: ObjectMessage) {
        val pubkey = recipient?.pubkey

        val nonceTrialsPerByte = pubkey?.nonceTrialsPerByte ?: NETWORK_NONCE_TRIALS_PER_BYTE
        val extraBytes = pubkey?.extraBytes ?: NETWORK_EXTRA_BYTES

        powRepo.putObject(`object`, nonceTrialsPerByte, extraBytes)
        if (`object`.payload is PlaintextHolder) {
            `object`.payload.plaintext?.let {
                it.initialHash = cryptography.getInitialHash(`object`)
                messageRepo.save(it)
            }
        }
        cryptography.doProofOfWork(`object`, nonceTrialsPerByte, extraBytes, this)
    }

    fun doProofOfWorkWithAck(plaintext: Plaintext, expirationTime: Long) {
        val ack = plaintext.ackMessage
        messageRepo.save(plaintext)
        val item = Item(ack!!, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES,
            expirationTime, plaintext)
        powRepo.putObject(item)
        cryptography.doProofOfWork(ack, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES, this)
    }

    override fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray) {
        val (`object`, _, _, expirationTime, message) = powRepo.getItem(initialHash)
        if (message == null) {
            `object`.nonce = nonce
            messageRepo.getMessage(initialHash)?.let {
                it.inventoryVector = `object`.inventoryVector
                it.updateNextTry()
                ctx.labeler.markAsSent(it)
                messageRepo.save(it)
            }
            try {
                ctx.networkListener.receive(`object`)
            } catch (e: IOException) {
                LOG.debug(e.message, e)
            }

            ctx.inventory.storeObject(`object`)
            ctx.networkHandler.offer(`object`.inventoryVector)
        } else {
            message.ackMessage!!.nonce = nonce
            val `object` = ObjectMessage.Builder()
                .stream(message.stream)
                .expiresTime(expirationTime!!)
                .payload(Msg(message))
                .build()
            if (`object`.isSigned) {
                `object`.sign(message.from.privateKey!!)
            }
            if (`object`.payload is Encrypted) {
                `object`.encrypt(message.to!!.pubkey!!)
            }
            doProofOfWork(message.to, `object`)
        }
        powRepo.removeObject(initialHash)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ProofOfWorkService::class.java)
    }
}
