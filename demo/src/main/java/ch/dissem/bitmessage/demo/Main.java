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

package ch.dissem.bitmessage.demo;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.networking.nio.NioNetworkHandler;
import ch.dissem.bitmessage.ports.NodeRegistry;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.wif.WifExporter;
import ch.dissem.bitmessage.wif.WifImporter;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null)
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR");
        if (System.getProperty("org.slf4j.simpleLogger.logFile") == null)
            System.setProperty("org.slf4j.simpleLogger.logFile", "./jabit.log");

        System.out.println("Version: " + BitmessageContext.getVersion());

        CmdLineOptions options = new CmdLineOptions();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
        }

        JdbcConfig jdbcConfig = new JdbcConfig();
        BitmessageContext.Builder ctxBuilder = new BitmessageContext.Builder()
            .addressRepo(new JdbcAddressRepository(jdbcConfig))
            .inventory(new JdbcInventory(jdbcConfig))
            .messageRepo(new JdbcMessageRepository(jdbcConfig))
            .powRepo(new JdbcProofOfWorkRepository(jdbcConfig))
            .networkHandler(new NioNetworkHandler())
            .cryptography(new BouncyCryptography());
        ctxBuilder.getPreferences().setPort(48444);
        if (options.localPort != null) {
            ctxBuilder.nodeRegistry(new NodeRegistry() {
                @Override
                public void cleanup() {
                    // NO OP
                }

                @Override
                public void remove(@NotNull NetworkAddress node) {
                    // NO OP
                }

                @Override
                public void update(@NotNull NetworkAddress node) {
                    // NO OP
                }

                @Override
                public void clear() {
                    // NO OP
                }

                @NotNull
                @Override
                public List<NetworkAddress> getKnownAddresses(int limit, @NotNull long... streams) {
                    return Arrays.stream(streams)
                        .mapToObj(s -> new NetworkAddress.Builder()
                            .ipv4(127, 0, 0, 1)
                            .port(options.localPort)
                            .stream(s).build())
                        .collect(Collectors.toList());
                }

                @Override
                public void offerAddresses(@NotNull List<NetworkAddress> nodes) {
                    LOG.info("Local node registry ignored offered addresses: " + nodes);
                }
            });
        } else {
            ctxBuilder.nodeRegistry(new JdbcNodeRegistry(jdbcConfig));
        }

        if (options.exportWIF != null || options.importWIF != null) {
            BitmessageContext ctx = ctxBuilder.build();

            if (options.exportWIF != null) {
                new WifExporter(ctx).addAll().write(options.exportWIF);
            }
            if (options.importWIF != null) {
                new WifImporter(ctx, options.importWIF).importAll();
            }
        } else {
            InetAddress syncServer = options.syncServer == null ? null : InetAddress.getByName(options.syncServer);
            new Application(ctxBuilder, syncServer, options.syncPort);
        }
    }

    private static class CmdLineOptions {
        @Option(name = "-local", usage = "Connect to local Bitmessage client on given port, instead of the usual connections from node.txt")
        private Integer localPort;

        @Option(name = "-import", usage = "Import from keys.dat or other WIF file.")
        private File importWIF;

        @Option(name = "-export", usage = "Export to WIF file.")
        private File exportWIF;

        @Option(name = "-syncServer", usage = "Use manual synchronization with the given server instead of starting a full node.")
        private String syncServer;

        @Option(name = "-syncPort", usage = "Port to use for synchronisation")
        private int syncPort = 8444;
    }
}
