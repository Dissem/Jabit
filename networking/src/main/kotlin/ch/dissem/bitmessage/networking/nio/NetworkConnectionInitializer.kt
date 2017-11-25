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

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.entity.*
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.exception.NodeException
import ch.dissem.bitmessage.utils.UnixTime
import org.slf4j.LoggerFactory

/**
 * Handles the initialization phase of  connection and, due to their design, custom commands.
 */
class NetworkConnectionInitializer(
    private val ctx: InternalContext,
    val node: NetworkAddress,
    val mode: Connection.Mode,
    val send: (MessagePayload) -> Unit,
    val markActive: (LongArray) -> Unit
) {
    private lateinit var version: Version

    private var verackSent: Boolean = false
    private var verackReceived: Boolean = false

    fun start() {
        if (mode == Connection.Mode.CLIENT || mode == Connection.Mode.SYNC) {
            send(Version(nonce = ctx.clientNonce, addrFrom = NetworkAddress.ANY, addrRecv = node, userAgent = ctx.preferences.userAgent))
        }
    }

    fun handleCommand(payload: MessagePayload) {
        when (payload.command) {
            MessagePayload.Command.VERSION -> handleVersion(payload as Version)
            MessagePayload.Command.VERACK -> {
                if (verackSent) {
                    activateConnection()
                }
                verackReceived = true
            }
            MessagePayload.Command.CUSTOM -> {
                ctx.customCommandHandler.handle(payload as CustomMessage)?.let { response ->
                    send(response)
                } ?: throw NodeException("No response for custom command available")
            }
            else -> throw NodeException("Command 'version' or 'verack' expected, but was '${payload.command}'")
        }
    }

    private fun handleVersion(version: Version) {
        if (version.nonce == ctx.clientNonce) {
            throw NodeException("Tried to connect to self, disconnecting.")
        } else if (version.version >= BitmessageContext.CURRENT_VERSION) {
            this.version = version
            verackSent = true
            send(VerAck())
            if (mode == Connection.Mode.SERVER) {
                send(Version.Builder().defaults(ctx.clientNonce).addrFrom(NetworkAddress.ANY).addrRecv(node).build())
            }
            if (verackReceived) {
                activateConnection()
            }
        } else {
            throw NodeException("Received unsupported version " + version.version + ", disconnecting.")
        }
    }

    private fun activateConnection() {
        LOG.info("Successfully established connection with node " + node)
        markActive(version.streams)
        node.time = UnixTime.now
        if (mode != Connection.Mode.SYNC) {
            sendAddresses()
            ctx.nodeRegistry.offerAddresses(listOf(node))
        }
        sendInventory()
    }


    private fun sendAddresses() {
        val addresses = ctx.nodeRegistry.getKnownAddresses(1000, *version.streams)
        send(Addr(addresses))
    }

    private fun sendInventory() {
        val inventory = ctx.inventory.getInventory(*version.streams)
        var i = 0
        while (i < inventory.size) {
            send(Inv(inventory.subList(i, Math.min(inventory.size, i + 50000))))
            i += 50000
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(NetworkConnectionInitializer::class.java)!!
    }
}
