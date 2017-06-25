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

package ch.dissem.bitmessage.networking.nio

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_EXTRA_BYTES
import ch.dissem.bitmessage.InternalContext.Companion.NETWORK_NONCE_TRIALS_PER_BYTE
import ch.dissem.bitmessage.entity.*
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException
import ch.dissem.bitmessage.ports.NetworkHandler
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.UnixTime
import ch.dissem.bitmessage.utils.UnixTime.MINUTE
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Contains everything used by both the old streams-oriented NetworkHandler and the new NioNetworkHandler,
 * respectively their connection objects.
 */
class Connection(
    private val ctx: InternalContext,
    val mode: Mode,
    val node: NetworkAddress,
    private val commonRequestedObjects: MutableMap<InventoryVector, Long>,
    syncTimeout: Long
) {
    private val requestedObjects: MutableSet<InventoryVector> = Collections.newSetFromMap(ConcurrentHashMap<InventoryVector, Boolean>(10000))

    internal val io = ConnectionIO(mode, syncTimeout, commonRequestedObjects, requestedObjects, { state }, this::handleMessage)
    private var initializer: NetworkConnectionInitializer? = NetworkConnectionInitializer(ctx, node, mode, io::send) { s ->
        state = State.ACTIVE
        streams = s
        initializer = null
    }

    private val listener: NetworkHandler.MessageListener = ctx.networkListener
    private val ivCache: MutableMap<InventoryVector, Long> = ConcurrentHashMap()

    private var lastObjectTime: Long = 0

    lateinit var streams: LongArray
        protected set

    @Volatile var state = State.CONNECTING
        private set

    val isSyncFinished
        get() = io.isSyncFinished

    val nothingToSend
        get() = io.sendingQueue.isEmpty()

    init {
        initializer!!.start()
    }

    fun send(payload: MessagePayload) = io.send(payload)

    protected fun handleMessage(payload: MessagePayload) {
        when (state) {
            State.CONNECTING -> initializer!!.handleCommand(payload)
            State.ACTIVE -> receiveMessage(payload)
            State.DISCONNECTED -> disconnect()
        }
    }

    private fun receiveMessage(messagePayload: MessagePayload) {
        when (messagePayload.command) {
            MessagePayload.Command.INV -> receiveMessage(messagePayload as Inv)
            MessagePayload.Command.GETDATA -> receiveMessage(messagePayload as GetData)
            MessagePayload.Command.OBJECT -> receiveMessage(messagePayload as ObjectMessage)
            MessagePayload.Command.ADDR -> receiveMessage(messagePayload as Addr)
            else -> throw IllegalStateException("Unexpectedly received '${messagePayload.command}' command")
        }
    }

    private fun receiveMessage(inv: Inv) {
        val originalSize = inv.inventory.size
        updateIvCache(inv.inventory)
        val missing = ctx.inventory.getMissing(inv.inventory, *streams)
        LOG.trace("Received inventory with $originalSize elements, of which are ${missing.size} missing.")
        io.send(GetData(missing - commonRequestedObjects.keys))
    }

    private fun receiveMessage(getData: GetData) {
        getData.inventory.forEach { iv -> ctx.inventory.getObject(iv)?.let { obj -> io.send(obj) } }
    }

    private fun receiveMessage(objectMessage: ObjectMessage) {
        requestedObjects.remove(objectMessage.inventoryVector)
        if (ctx.inventory.contains(objectMessage)) {
            LOG.trace("Received object " + objectMessage.inventoryVector + " - already in inventory")
            return
        }
        try {
            listener.receive(objectMessage)
            cryptography().checkProofOfWork(objectMessage, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES)
            ctx.inventory.storeObject(objectMessage)
            // offer object to some random nodes so it gets distributed throughout the network:
            ctx.networkHandler.offer(objectMessage.inventoryVector)
            lastObjectTime = UnixTime.now
        } catch (e: InsufficientProofOfWorkException) {
            LOG.warn(e.message)
            // DebugUtils.saveToFile(objectMessage); // this line must not be committed active
        } catch (e: IOException) {
            LOG.error("Stream " + objectMessage.stream + ", object type " + objectMessage.type + ": " + e.message, e)
        } finally {
            if (commonRequestedObjects.remove(objectMessage.inventoryVector) == null) {
                LOG.debug("Received object that wasn't requested.")
            }
        }
    }

    private fun receiveMessage(addr: Addr) {
        LOG.trace("Received " + addr.addresses.size + " addresses.")
        ctx.nodeRegistry.offerAddresses(addr.addresses)
    }

    private fun updateIvCache(inventory: List<InventoryVector>) {
        cleanupIvCache()
        val now = UnixTime.now
        for (iv in inventory) {
            ivCache.put(iv, now)
        }
    }

    fun offer(iv: InventoryVector) {
        io.send(Inv(listOf(iv)))
        updateIvCache(listOf(iv))
    }

    fun knowsOf(iv: InventoryVector): Boolean {
        return ivCache.containsKey(iv)
    }

    fun requested(iv: InventoryVector): Boolean {
        return requestedObjects.contains(iv)
    }

    private fun cleanupIvCache() {
        val fiveMinutesAgo = UnixTime.now - 5 * MINUTE
        for ((key, value) in ivCache) {
            if (value < fiveMinutesAgo) {
                ivCache.remove(key)
            }
        }
    }

    // the TCP timeout starts out at 20 seconds
    // after verack messages are exchanged, the timeout is raised to 10 minutes
    fun isExpired(): Boolean = when (state) {
        State.CONNECTING -> io.lastUpdate < System.currentTimeMillis() - 20000
        State.ACTIVE -> io.lastUpdate < System.currentTimeMillis() - 600000
        State.DISCONNECTED -> true
    }

    fun disconnect() {
        state = State.DISCONNECTED
        io.disconnect()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Connection) return false
        return node == other.node
    }

    override fun hashCode(): Int {
        return Objects.hash(node)
    }

    enum class State {
        CONNECTING, ACTIVE, DISCONNECTED
    }

    enum class Mode {
        SERVER, CLIENT, SYNC
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Connection::class.java)
    }
}
