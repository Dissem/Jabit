/*
 * Copyright 2016 Christian Basler
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

package ch.dissem.bitmessage.networking.nio

import ch.dissem.bitmessage.constants.Network.HEADER_SIZE
import ch.dissem.bitmessage.entity.GetData
import ch.dissem.bitmessage.entity.MessagePayload
import ch.dissem.bitmessage.entity.NetworkMessage
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.factory.V3MessageReader
import ch.dissem.bitmessage.utils.UnixTime
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Represents the current state of a connection.
 */
class ConnectionIO(
    private val mode: Connection.Mode,
    syncTimeout: Long,
    private val commonRequestedObjects: MutableMap<InventoryVector, Long>,
    private val requestedObjects: MutableSet<InventoryVector>,
    private val getState: () -> Connection.State,
    private val handleMessage: (MessagePayload) -> Unit
) {
    private val headerOut: ByteBuffer = ByteBuffer.allocate(HEADER_SIZE)
    private var payloadOut: ByteBuffer? = null
    private var reader: V3MessageReader? = V3MessageReader()
    internal val sendingQueue: Deque<MessagePayload> = ConcurrentLinkedDeque<MessagePayload>()

    internal var lastUpdate = System.currentTimeMillis()
        private set

    private val syncTimeout: Long = if (syncTimeout > 0) UnixTime.now + syncTimeout else 0
    private var syncReadTimeout = java.lang.Long.MAX_VALUE

    init {
        headerOut.flip()
    }

    val inBuffer: ByteBuffer
        get() = reader?.getActiveBuffer() ?: throw NodeException("Node is disconnected")

    fun updateWriter() {
        if (!headerOut.hasRemaining() && !sendingQueue.isEmpty()) {
            headerOut.clear()
            val payload = sendingQueue.poll()
            payloadOut = NetworkMessage(payload).writer().writeHeaderAndGetPayloadBuffer(headerOut)
            headerOut.flip()
            lastUpdate = System.currentTimeMillis()
        }
    }

    val outBuffers: Array<ByteBuffer>
        get() = payloadOut?.let { arrayOf(headerOut, it) } ?: arrayOf(headerOut)

    fun cleanupBuffers() {
        payloadOut?.let {
            if (!it.hasRemaining()) payloadOut = null
        }
    }

    fun updateReader() {
        reader?.let { reader ->
            reader.update()
            if (!reader.getMessages().isEmpty()) {
                val iterator = reader.getMessages().iterator()
                var msg: NetworkMessage? = null
                while (iterator.hasNext()) {
                    msg = iterator.next()
                    handleMessage(msg.payload)
                    iterator.remove()
                }
                isSyncFinished = syncFinished(msg)
            }
            lastUpdate = System.currentTimeMillis()
        }
    }

    fun updateSyncStatus() {
        if (!isSyncFinished) {
            isSyncFinished = reader?.getMessages()?.isEmpty() ?: true && syncFinished(null)
        }
    }

    protected fun syncFinished(msg: NetworkMessage?): Boolean {
        if (mode != Connection.Mode.SYNC) {
            return false
        }
        if (Thread.interrupted() || getState() == Connection.State.DISCONNECTED) {
            return true
        }
        if (getState() == Connection.State.CONNECTING) {
            return false
        }
        if (syncTimeout < UnixTime.now) {
            LOG.info("Synchronization timed out")
            return true
        }
        if (!nothingToSend()) {
            syncReadTimeout = System.currentTimeMillis() + 1000
            return false
        }
        if (msg == null) {
            return syncReadTimeout < System.currentTimeMillis()
        } else {
            syncReadTimeout = System.currentTimeMillis() + 1000
            return false
        }
    }

    fun disconnect() {
        reader?.let {
            it.cleanup()
            reader = null
        }
        payloadOut = null
    }

    fun send(payload: MessagePayload) {
        sendingQueue.add(payload)
        if (payload is GetData) {
            val now = UnixTime.now
            val inventory = payload.inventory
            requestedObjects.addAll(inventory)
            inventory.forEach { iv -> commonRequestedObjects.put(iv, now) }
        }
    }

    var isSyncFinished = false

    val isWritePending: Boolean
        get() = !sendingQueue.isEmpty()
            || headerOut.hasRemaining()
            || payloadOut?.hasRemaining() ?: false

    fun nothingToSend() = sendingQueue.isEmpty()

    companion object {
        val LOG = LoggerFactory.getLogger(ConnectionIO::class.java)
    }
}
