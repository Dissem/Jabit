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
import ch.dissem.bitmessage.inventory.SimpleAddressRepository;
import ch.dissem.bitmessage.inventory.SimpleInventory;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.ports.NetworkHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by chris on 06.04.15.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        NetworkNode networkNode = new NetworkNode();
        Context.init(new SimpleInventory(), new SimpleAddressRepository(), networkNode, 48444);
        Context.getInstance().addStream(1);
        networkNode.setListener(new NetworkHandler.MessageListener() {
            @Override
            public void receive(ObjectPayload payload) {
                // TODO
            }
        });
        networkNode.start();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter String");
        br.readLine();
    }
}
