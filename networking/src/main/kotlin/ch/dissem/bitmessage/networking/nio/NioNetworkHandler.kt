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

import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.constants.Network.HEADER_SIZE
import ch.dissem.bitmessage.constants.Network.NETWORK_MAGIC_NUMBER
import ch.dissem.bitmessage.entity.CustomMessage
import ch.dissem.bitmessage.entity.GetData
import ch.dissem.bitmessage.entity.NetworkMessage
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.factory.V3MessageReader
import ch.dissem.bitmessage.networking.nio.Connection.Mode.*
import ch.dissem.bitmessage.ports.NetworkHandler
import ch.dissem.bitmessage.utils.Collections.selectRandom
import ch.dissem.bitmessage.utils.DebugUtils
import ch.dissem.bitmessage.utils.Property
import ch.dissem.bitmessage.utils.ThreadFactoryBuilder.Companion.pool
import ch.dissem.bitmessage.utils.UnixTime.now
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.SelectionKey.*
import java.util.*
import java.util.concurrent.*

/**
 * Network handler using java.nio, resulting in less threads.
 */
class NioNetworkHandler : NetworkHandler, InternalContext.ContextHolder {

    private val threadPool = Executors.newCachedThreadPool(
        pool("network")
            .lowPrio()
            .daemon()
            .build())

    private lateinit var ctx: InternalContext
    private var selector: Selector? = null
    private var serverChannel: ServerSocketChannel? = null
    private val connectionQueue = ConcurrentLinkedQueue<NetworkAddress>()
    private val connections = ConcurrentHashMap<Connection, SelectionKey>()
    private val requestedObjects = ConcurrentHashMap<InventoryVector, Long>(10000)

    private var starter: Thread? = null

    override fun setContext(context: InternalContext) {
        ctx = context
    }

    override fun synchronize(server: InetAddress, port: Int, timeoutInSeconds: Long): Future<Void> {
        return threadPool.submit(Callable<Void> {
            SocketChannel.open(InetSocketAddress(server, port)).use { channel ->
                channel.configureBlocking(false)
                val connection = Connection(ctx, SYNC,
                    NetworkAddress.Builder().ip(server).port(port).stream(1).build(),
                    HashMap<InventoryVector, Long>(), timeoutInSeconds)
                while (channel.isConnected && !connection.isSyncFinished) {
                    write(channel, connection.io)
                    read(channel, connection.io)
                    Thread.sleep(10)
                }
                LOG.info("Synchronization finished")
            }
            null
        })
    }

    override fun send(server: InetAddress, port: Int, request: CustomMessage): CustomMessage {
        SocketChannel.open(InetSocketAddress(server, port)).use { channel ->
            channel.configureBlocking(true)
            val headerBuffer = ByteBuffer.allocate(HEADER_SIZE)
            val payloadBuffer = NetworkMessage(request).writeHeaderAndGetPayloadBuffer(headerBuffer)
            headerBuffer.flip()
            while (headerBuffer.hasRemaining()) {
                channel.write(headerBuffer)
            }
            while (payloadBuffer.hasRemaining()) {
                channel.write(payloadBuffer)
            }

            val reader = V3MessageReader()
            while (channel.isConnected && reader.getMessages().isEmpty()) {
                if (channel.read(reader.getActiveBuffer()) > 0) {
                    reader.update()
                } else {
                    throw NodeException("No response from node $server")
                }
            }
            val networkMessage: NetworkMessage?
            if (reader.getMessages().isEmpty()) {
                throw NodeException("No response from node " + server)
            } else {
                networkMessage = reader.getMessages().first()
            }

            if (networkMessage.payload is CustomMessage) {
                return networkMessage.payload as CustomMessage
            } else {
                throw NodeException("Unexpected response from node $server: ${networkMessage.payload.javaClass}")
            }
        }
    }

    override fun start() {
        if (selector?.isOpen ?: false) {
            throw IllegalStateException("Network already running - you need to stop first.")
        }
        val selector = Selector.open()
        this.selector = selector

        requestedObjects.clear()

        starter = thread("connection manager") {
            while (selector.isOpen) {
                var missing = NETWORK_MAGIC_NUMBER
                for ((connection, _) in connections) {
                    if (connection.state == Connection.State.ACTIVE) {
                        missing--
                        if (missing == 0) break
                    }
                }
                if (missing > 0) {
                    var addresses = ctx.nodeRegistry.getKnownAddresses(100, *ctx.streams)
                    addresses = selectRandom(missing, addresses)
                    for (address in addresses) {
                        if (!isConnectedTo(address)) {
                            connectionQueue.offer(address)
                        }
                    }
                }

                val it = connections.entries.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    if (!e.value.isValid || e.key.isExpired()) {
                        try {
                            e.value.channel().close()
                        } catch (ignore: Exception) {
                        }

                        e.value.cancel()
                        e.value.attach(null)
                        e.key.disconnect()
                        it.remove()
                    }
                }

                // The list 'requested objects' helps to prevent downloading an object
                // twice. From time to time there is an error though, and an object is
                // never downloaded. To prevent a large list of failed objects and give
                // them a chance to get downloaded again, we will attempt to download an
                // object from another node after some time out.
                val timedOut = System.currentTimeMillis() - REQUESTED_OBJECTS_MAX_TIME
                val delayed = mutableListOf<InventoryVector>()
                val iterator = requestedObjects.entries.iterator()
                while (iterator.hasNext()) {
                    val e = iterator.next()

                    if (e.value == DELAYED) {
                        iterator.remove()
                    } else if (e.value < timedOut) {
                        delayed.add(e.key)
                        e.setValue(DELAYED)
                    }
                }
                request(delayed)

                try {
                    Thread.sleep(30000)
                } catch (e: InterruptedException) {
                    return@thread
                }
            }
        }

        thread("selector worker", {
            try {
                val serverChannel = ServerSocketChannel.open()
                this.serverChannel = serverChannel
                serverChannel.configureBlocking(false)
                serverChannel.socket().bind(InetSocketAddress(ctx.port))
                serverChannel.register(selector, OP_ACCEPT, null)

                while (selector.isOpen) {
                    selector.select(1000)
                    val keyIterator = selector.selectedKeys().iterator()
                    while (keyIterator.hasNext()) {
                        val key = keyIterator.next()
                        keyIterator.remove()
                        if (key.attachment() == null) {
                            try {
                                if (key.isAcceptable) {
                                    // handle accept
                                    try {
                                        val accepted = (key.channel() as ServerSocketChannel).accept()
                                        accepted.configureBlocking(false)
                                        val connection = Connection(ctx, SERVER,
                                            NetworkAddress(
                                                time = now,
                                                stream = 1L,
                                                socket = accepted.socket()!!
                                            ),
                                            requestedObjects, 0
                                        )
                                        connections.put(
                                            connection,
                                            accepted.register(selector, OP_READ or OP_WRITE, connection)
                                        )
                                    } catch (e: AsynchronousCloseException) {
                                        LOG.trace(e.message)
                                    } catch (e: IOException) {
                                        LOG.error(e.message, e)
                                    }

                                }
                            } catch (e: CancelledKeyException) {
                                LOG.debug(e.message, e)
                            }

                        } else {
                            // handle read/write
                            val channel = key.channel() as SocketChannel
                            val connection = key.attachment() as Connection
                            try {
                                if (key.isConnectable) {
                                    if (!channel.finishConnect()) {
                                        continue
                                    }
                                }
                                if (key.isWritable) {
                                    write(channel, connection.io)
                                }
                                if (key.isReadable) {
                                    read(channel, connection.io)
                                }
                                if (connection.state == Connection.State.DISCONNECTED) {
                                    key.interestOps(0)
                                    channel.close()
                                } else if (connection.io.isWritePending) {
                                    key.interestOps(OP_READ or OP_WRITE)
                                } else {
                                    key.interestOps(OP_READ)
                                }
                            } catch (e: CancelledKeyException) {
                                connection.disconnect()
                            } catch (e: NodeException) {
                                connection.disconnect()
                            } catch (e: IOException) {
                                connection.disconnect()
                            }
                        }
                    }
                    // set interest ops
                    for ((connection, selectionKey) in connections) {
                        try {
                            if (selectionKey.isValid
                                && selectionKey.interestOps() and OP_WRITE == 0
                                && selectionKey.interestOps() and OP_CONNECT == 0
                                && !connection.nothingToSend) {
                                selectionKey.interestOps(OP_READ or OP_WRITE)
                            }
                        } catch (x: CancelledKeyException) {
                            connection.disconnect()
                        }

                    }
                    // start new connections
                    if (!connectionQueue.isEmpty()) {
                        val address = connectionQueue.poll()
                        try {
                            val channel = SocketChannel.open()
                            channel.configureBlocking(false)
                            channel.connect(InetSocketAddress(address.toInetAddress(), address.port))
                            val connection = Connection(ctx, CLIENT, address, requestedObjects, 0)
                            connections.put(
                                connection,
                                channel.register(selector, OP_CONNECT, connection)
                            )
                        } catch (ignore: NoRouteToHostException) {
                            // We'll try to connect to many offline nodes, so
                            // this is expected to happen quite a lot.
                        } catch (e: AsynchronousCloseException) {
                            // The exception is expected if the network is being
                            // shut down, as we actually do asynchronously close
                            // the connections.
                            if (isRunning) {
                                LOG.error(e.message, e)
                            }
                        } catch (e: IOException) {
                            LOG.error(e.message, e)
                        }

                    }
                }
                selector.close()
            } catch (_: ClosedSelectorException) {
            }
        })
    }

    private fun thread(threadName: String, runnable: () -> Unit): Thread {
        val thread = Thread(runnable, threadName)
        thread.isDaemon = true
        thread.priority = Thread.MIN_PRIORITY
        thread.start()
        return thread
    }

    override fun stop() {
        serverChannel?.socket()?.close()
        selector?.close()
        for (selectionKey in connections.values) {
            selectionKey.channel().close()
        }
    }

    override fun offer(iv: InventoryVector) {
        val targetConnections = connections.keys.filter { it.state == Connection.State.ACTIVE && !it.knowsOf(iv) }
        selectRandom(NETWORK_MAGIC_NUMBER, targetConnections).forEach { it.offer(iv) }
    }

    override fun request(inventoryVectors: MutableCollection<InventoryVector>) {
        if (!isRunning) {
            requestedObjects.clear()
            return
        }
        val iterator = inventoryVectors.iterator()
        if (!iterator.hasNext()) {
            return
        }

        val distribution = HashMap<Connection, MutableList<InventoryVector>>()
        for ((connection, _) in connections) {
            if (connection.state == Connection.State.ACTIVE) {
                distribution.put(connection, mutableListOf<InventoryVector>())
            }
        }
        if (distribution.isEmpty()) {
            return
        }
        var next = iterator.next()
        var previous: Connection? = null
        do {
            for (connection in distribution.keys) {
                if (connection === previous || previous == null) {
                    if (iterator.hasNext()) {
                        previous = connection
                        next = iterator.next()
                    } else {
                        break
                    }
                }
                if (connection.knowsOf(next) && !connection.requested(next)) {
                    val ivs = distribution[connection] ?: throw IllegalStateException("distribution not available for $connection")
                    if (ivs.size == GetData.MAX_INVENTORY_SIZE) {
                        connection.send(GetData(ivs))
                        ivs.clear()
                    }
                    ivs.add(next)
                    iterator.remove()

                    if (iterator.hasNext()) {
                        next = iterator.next()
                        previous = connection
                    } else {
                        break
                    }
                }
            }
        } while (iterator.hasNext())

        // remove objects nobody knows of
        for (iv in inventoryVectors) {
            requestedObjects.remove(iv)
        }

        for (connection in distribution.keys) {
            val ivs = distribution[connection] ?: throw IllegalStateException("distribution not available for $connection")
            if (!ivs.isEmpty()) {
                connection.send(GetData(ivs))
            }
        }
    }

    override fun getNetworkStatus(): Property {
        val streams = TreeSet<Long>()
        val incomingConnections = TreeMap<Long, Int>()
        val outgoingConnections = TreeMap<Long, Int>()

        connections.keys
            .filter { it.state == Connection.State.ACTIVE }
            .forEach {
                for (stream in it.streams) {
                    streams.add(stream)
                    if (it.mode == SERVER) {
                        DebugUtils.inc(incomingConnections, stream)
                    } else {
                        DebugUtils.inc(outgoingConnections, stream)
                    }
                }
            }
        val streamProperties = mutableListOf<Property>()
        for (stream in streams) {
            val incoming = incomingConnections[stream] ?: 0
            val outgoing = outgoingConnections[stream] ?: 0
            streamProperties.add(Property("stream " + stream, Property("nodes", incoming + outgoing),
                Property("incoming", incoming),
                Property("outgoing", outgoing)
            ))
        }
        return Property("network",
            Property("connectionManager", if (isRunning) "running" else "stopped"),
            Property("connections", streamProperties),
            Property("requestedObjects", requestedObjects.size)
        )
    }

    private fun isConnectedTo(address: NetworkAddress): Boolean = connections.any { it.key.node == address }

    override val isRunning: Boolean
        get() = selector?.isOpen ?: false && starter?.isAlive ?: false

    companion object {
        private val LOG = LoggerFactory.getLogger(NioNetworkHandler::class.java)
        private val REQUESTED_OBJECTS_MAX_TIME = (2 * 60000).toLong() // 2 minutes in ms
        private val DELAYED = java.lang.Long.MIN_VALUE

        private fun write(channel: SocketChannel, connection: ConnectionIO) {
            writeBuffer(connection.outBuffers, channel)

            connection.updateWriter()

            writeBuffer(connection.outBuffers, channel)
            connection.cleanupBuffers()
        }

        private fun writeBuffer(buffers: Array<ByteBuffer>, channel: SocketChannel) {
            if (buffers.any { buf -> buf.hasRemaining() }) channel.write(buffers)
        }

        private fun read(channel: SocketChannel, connection: ConnectionIO) {
            if (channel.read(connection.inBuffer) > 0) {
                connection.updateReader()
            }
            connection.updateSyncStatus()
        }
    }
}
