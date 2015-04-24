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
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.inventory.JdbcAddressRepository;
import ch.dissem.bitmessage.inventory.JdbcInventory;
import ch.dissem.bitmessage.inventory.JdbcNodeRegistry;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Scanner;

/**
 * Created by chris on 06.04.15.
 */
public class Main {
    private final static Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        BitmessageContext ctx = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository())
                .inventory(new JdbcInventory())
                .nodeRegistry(new JdbcNodeRegistry())
                .networkHandler(new NetworkNode())
                .port(48444)
                .streams(1)
                .build();
        ctx.getNetworkHandler().start(new NetworkHandler.MessageListener() {
            @Override
            public void receive(ObjectPayload payload) {
//                LOG.info("message received: " + payload);
//                System.out.print('.');
            }
        });

        System.out.print("Press Enter to exit\n");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        LOG.info("Shutting down client");
        ctx.getNetworkHandler().stop();
    }
}
