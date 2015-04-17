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

import ch.dissem.bitmessage.Context;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.inventory.DatabaseRepository;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.ports.NetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by chris on 06.04.15.
 */
public class Main {
    private final static Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        NetworkNode networkNode = new NetworkNode();
        DatabaseRepository repo = new DatabaseRepository();
        Context.init(repo, repo, networkNode, 48444);
        Context.getInstance().addStream(1);
        networkNode.start(new NetworkHandler.MessageListener() {
            @Override
            public void receive(ObjectPayload payload) {
//                LOG.info("message received: " + payload);
//                System.out.print('.');
            }
        });

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Press Enter to exit\n");
        br.readLine();
        LOG.info("Shutting down client");
        networkNode.stop();
    }
}
