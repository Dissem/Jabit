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

package ch.dissem.bitmessage.networking

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.CustomMessage
import ch.dissem.bitmessage.entity.MessagePayload
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.networking.nio.NioNetworkHandler
import ch.dissem.bitmessage.ports.CustomCommandHandler
import ch.dissem.bitmessage.ports.NetworkHandler
import ch.dissem.bitmessage.testutils.TestInventory
import ch.dissem.bitmessage.utils.Property
import ch.dissem.bitmessage.utils.Singleton.cryptography
import com.nhaarman.mockito_kotlin.mock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Tests network handlers. This test is parametrized, so it can test both the nio and classic implementation
 * as well as their combinations. It might be slightly over the top and will most probably be cleaned up once
 * the nio implementation is deemed stable.
 */
class NetworkHandlerTest {

    private lateinit var peerInventory: TestInventory
    private lateinit var nodeInventory: TestInventory

    private lateinit var peer: BitmessageContext
    private lateinit var node: BitmessageContext

    private lateinit var peerNetworkHandler: NetworkHandler
    private lateinit var nodeNetworkHandler: NetworkHandler

    @BeforeEach
    fun setUp() {
        peerInventory = TestInventory()
        peerNetworkHandler = NioNetworkHandler()
        peer = BitmessageContext.build {
            cryptography = BouncyCryptography()
            inventory = peerInventory
            nodeRegistry = TestNodeRegistry()
            networkHandler = peerNetworkHandler
            addressRepo = mock()
            labelRepo = mock()
            messageRepo = mock()
            proofOfWorkRepo = mock()
            customCommandHandler = object : CustomCommandHandler {
                override fun handle(request: CustomMessage): MessagePayload? {
                    val data = request.getData()
                    if (data.isNotEmpty()) {
                        when (data[0]) {
                            0.toByte() -> return null
                            1.toByte() -> {
                            }
                            3.toByte() -> data[0] = 0
                        }
                    }
                    return CustomMessage("test response", request.getData())
                }
            }
            listener = mock()
            preferences.port = peerAddress.port
        }
        peer.startup()
        Thread.sleep(100)

        nodeInventory = TestInventory()
        nodeNetworkHandler = NioNetworkHandler()
        node = BitmessageContext.build {
            cryptography = BouncyCryptography()
            inventory = nodeInventory
            nodeRegistry = TestNodeRegistry(peerAddress)
            networkHandler = nodeNetworkHandler
            addressRepo = mock()
            labelRepo = mock()
            messageRepo = mock()
            proofOfWorkRepo = mock()
            customCommandHandler = object : CustomCommandHandler {
                override fun handle(request: CustomMessage): MessagePayload? {
                    val data = request.getData()
                    if (data.isNotEmpty()) {
                        when (data[0]) {
                            0.toByte() -> return null
                            1.toByte() -> {
                            }
                            3.toByte() -> data[0] = 0
                        }
                    }
                    return CustomMessage("test response", request.getData())
                }
            }
            listener = mock()
            preferences.port = 6002
        }
    }

    @AfterEach
    fun cleanUp() {
        shutdown(peer)
        shutdown(node)
        shutdown(nodeNetworkHandler)
    }

    private fun waitForNetworkStatus(ctx: BitmessageContext): Property {
        var status: Property?
        do {
            Thread.sleep(100)
            status = ctx.status().getProperty("network", "connections", "stream 1")
        } while (status == null)
        return status
    }

    @Test
    fun `ensure nodes are connecting`() {
        node.startup()

        val nodeStatus = waitForNetworkStatus(node)
        val peerStatus = waitForNetworkStatus(peer)

        assertEquals(1, nodeStatus.getProperty("outgoing")?.value)
        assertEquals(1, peerStatus.getProperty("incoming")?.value)
    }

    @Test
    fun `ensure CustomMessage is sent and response retrieved`() {
        val data = cryptography().randomBytes(8)
        data[0] = 1.toByte()
        val request = CustomMessage("test request", data)
        node.startup()

        val response = nodeNetworkHandler.send(peerAddress.toInetAddress(), peerAddress.port, request)

        assertNotNull(response)
        assertEquals("test response", response.customCommand)
        assertEquals(data, response.getData())
    }

    @Test
    fun `ensure CustomMessage without response yields exception`() {
        assertThrows(NodeException::class.java) {
            val data = cryptography().randomBytes(8)
            data[0] = 0.toByte()
            val request = CustomMessage("test request", data)

            nodeNetworkHandler.send(peerAddress.toInetAddress(), peerAddress.port, request)
        }
    }

    @Test
    fun `ensure objects are synchronized if both have objects`() {
        peerInventory.init(
            "V4Pubkey.payload",
            "V5Broadcast.payload"
        )

        nodeInventory.init(
            "V1Msg.payload",
            "V4Pubkey.payload"
        )

        val future = nodeNetworkHandler.synchronize(peerAddress.toInetAddress(), peerAddress.port, 10)
        future.get()
        assertInventorySize(3, nodeInventory)
        assertInventorySize(3, peerInventory)
    }

    @Test
    fun `ensure objects are synchronized if only peer has objects`() {
        peerInventory.init(
            "V4Pubkey.payload",
            "V5Broadcast.payload"
        )

        nodeInventory.init()

        val future = nodeNetworkHandler.synchronize(peerAddress.toInetAddress(), peerAddress.port, 10)
        future.get()
        assertInventorySize(2, nodeInventory)
        assertInventorySize(2, peerInventory)
    }

    @Test
    fun `ensure objects are synchronized if only node has objects`() {
        peerInventory.init()

        nodeInventory.init(
            "V1Msg.payload"
        )

        val future = nodeNetworkHandler.synchronize(peerAddress.toInetAddress(), peerAddress.port, 10)
        future.get()
        assertInventorySize(1, nodeInventory)
        assertInventorySize(1, peerInventory)
    }

    private fun assertInventorySize(expected: Int, inventory: TestInventory) {
        val timeout = System.currentTimeMillis() + 1000
        while (expected != inventory.getInventory().size && System.currentTimeMillis() < timeout) {
            Thread.sleep(10)
        }
        assertEquals(expected.toLong(), inventory.getInventory().size.toLong())
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NetworkHandlerTest::class.java)
        private val peerAddress = NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(6001).build()

        private fun shutdown(ctx: BitmessageContext) {
            if (!ctx.isRunning()) return

            ctx.shutdown()
            do {
                try {
                    Thread.sleep(100)
                } catch (ignore: InterruptedException) {
                }
            } while (ctx.isRunning())
        }

        private fun shutdown(networkHandler: NetworkHandler) {
            if (!networkHandler.isRunning) return

            networkHandler.stop()
            do {
                try {
                    Thread.sleep(100)
                } catch (ignore: InterruptedException) {
                    if (networkHandler.isRunning) {
                        LOG.warn("Thread interrupted while waiting for network shutdown - " + "this could cause problems in subsequent tests.")
                    }
                    return
                }

            } while (networkHandler.isRunning)
        }
    }

}
