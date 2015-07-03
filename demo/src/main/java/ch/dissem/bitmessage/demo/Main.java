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
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.repository.*;
import ch.dissem.bitmessage.wif.WifExporter;
import ch.dissem.bitmessage.wif.WifImporter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null)
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR");
        if (System.getProperty("org.slf4j.simpleLogger.logFile") == null)
            System.setProperty("org.slf4j.simpleLogger.logFile", "./jabit.log");

        CmdLineOptions options = new CmdLineOptions();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.err);
        }
        if (options.exportWIF != null || options.importWIF != null) {
            JdbcConfig jdbcConfig = new JdbcConfig();
            BitmessageContext ctx = new BitmessageContext.Builder()
                    .addressRepo(new JdbcAddressRepository(jdbcConfig))
                    .inventory(new JdbcInventory(jdbcConfig))
                    .nodeRegistry(new MemoryNodeRegistry())
                    .messageRepo(new JdbcMessageRepository(jdbcConfig))
                    .networkHandler(new NetworkNode())
                    .port(48444)
                    .build();

            if (options.exportWIF != null) {
                new WifExporter(ctx).addAll().write(options.exportWIF);
            }
            if (options.importWIF != null) {
                new WifImporter(ctx, options.importWIF).importAll();
            }
        } else {
            new Application();
        }
    }

    private static class CmdLineOptions {
        @Option(name = "-import", usage = "Import from keys.dat or other WIF file.")
        private File importWIF;

        @Option(name = "-export", usage = "Export to WIF file.")
        private File exportWIF;
    }
}
