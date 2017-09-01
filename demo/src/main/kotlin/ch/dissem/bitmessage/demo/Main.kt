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

package ch.dissem.bitmessage.demo

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.networking.nio.NioNetworkHandler
import ch.dissem.bitmessage.ports.NodeRegistry
import ch.dissem.bitmessage.repository.*
import ch.dissem.bitmessage.wif.WifExporter
import ch.dissem.bitmessage.wif.WifImporter
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress

class Main {

    companion object {
        private val log = LoggerFactory.getLogger(Main::class.java)

        @JvmStatic fun main(args: Array<String>) {
            when {
                System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null ->
                    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
                System.getProperty("org.slf4j.simpleLogger.logFile") == null ->
                    System.setProperty("org.slf4j.simpleLogger.logFile", "./jabit.log")
            }

            println("Version: ${BitmessageContext.version}")

            val options = CmdLineoptions()
            val parser = CmdLineParser(options)
            try {
                parser.parseArgument(*args)
            } catch (e: CmdLineException) {
                parser.printUsage(System.err)
            }

            val jdbcConfig = JdbcConfig()
            val ctxBuilder = BitmessageContext.Builder()
                .addressRepo(JdbcAddressRepository(jdbcConfig))
                .inventory(JdbcInventory(jdbcConfig))
                .messageRepo(JdbcMessageRepository(jdbcConfig))
                .powRepo(JdbcProofOfWorkRepository(jdbcConfig))
                .networkHandler(NioNetworkHandler())
                .cryptography(BouncyCryptography())
                .port(48444)

            if (options.localPort != null) {
                ctxBuilder.nodeRegistry(object : NodeRegistry {
                    override fun clear() { /*NO OP*/ }

                    override fun getKnownAddresses(limit: Int, vararg streams: Long): List<NetworkAddress> {
                        return streams.map {
                            NetworkAddress.Builder()
                                .ipv4(127, 0, 0, 1)
                                .port(options.localPort!!)
                                .stream(it)
                                .build()
                        }
                    }

                    override fun offerAddresses(nodes: List<NetworkAddress>) {
                        log.info("Local node registry ignored offered addresses: $nodes")
                    }
                })
            } else {
                ctxBuilder.nodeRegistry(JdbcNodeRegistry(jdbcConfig))
            }

            if (options.exportWIF != null || options.importWIF != null) {
                val ctx = ctxBuilder.build()
                when {
                    options.exportWIF != null -> WifExporter(ctx).addAll().write(options.exportWIF!!)
                    options.importWIF != null -> WifImporter(ctx, options.importWIF!!).importAll()
                }
            } else {
                val syncServer = if (options.syncServer == null) null else InetAddress.getByName(options.syncServer)
                Application(ctxBuilder, syncServer, options.syncPort)
            }
        }
    }

    private class CmdLineoptions {
        @Option(name = "-local", usage = "Connect to local Bitmessage client on given port, instead of the usual connections from node.txt")
        var localPort: Int? = null

        @Option(name = "-import", usage = "Import from keys.dat or other WIF file.")
        var importWIF: File? = null

        @Option(name = "-export", usage = "Export to WIF file.")
        var exportWIF: File? = null

        @Option(name = "-syncServer", usage = "Use manual synchronization with the given server instead of starting a full node.")
        var syncServer: String? = null

        @Option(name = "-syncPort", usage = "Port to use for synchronisation")
        var syncPort = 8444
    }
}
