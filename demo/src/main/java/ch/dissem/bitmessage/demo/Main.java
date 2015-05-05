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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.inventory.JdbcInventory;
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Encode;
import ch.dissem.bitmessage.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by chris on 06.04.15.
 */
public class Main {
    private final static Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        final BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");

//        BitmessageContext ctx = new BitmessageContext.Builder()
//                .addressRepo(new JdbcAddressRepository())
//                .inventory(new JdbcInventory())
//                .nodeRegistry(new JdbcNodeRegistry())
//                .networkHandler(new NetworkNode())
//                .port(48444)
//                .streams(1)
//                .build();
//
//        ctx.getNetworkHandler().start(new NetworkHandler.MessageListener() {
//            @Override
//            public void receive(ObjectPayload payload) {
////                LOG.info("message received: " + payload);
////                System.out.print('.');
//                if (payload instanceof V3Pubkey) {
//                    V3Pubkey pubkey = (V3Pubkey) payload;
//                    try {
//                        address.setPubkey(pubkey);
//                        System.out.println(address);
//                    } catch (Exception ignore) {
//                        System.err.println("Received pubkey we didn't request.");
//                    }
//                }
//            }
//        });
//
//        Scanner scanner = new Scanner(System.in);
//        System.out.println("Press Enter to request pubkey for address " + address);
//        scanner.nextLine();
//        ctx.send(1, address.getVersion(), new GetPubkey(address), 3000, 1000, 1000);
//
//        System.out.println("Press Enter to exit");
//        scanner.nextLine();
//        LOG.info("Shutting down client");
//        ctx.getNetworkHandler().stop();


        List<ObjectMessage> objects = new JdbcInventory().getObjects(address.getStream(), address.getVersion(), ObjectType.PUBKEY);
        System.out.println("Address version: " + address.getVersion());
        System.out.println("Address stream:  " + address.getStream());
        for (ObjectMessage o : objects) {
//            if (!o.isSignatureValid()) System.out.println("Invalid signature.");
//            System.out.println(o.getPayload().getSignature().length);
            Pubkey pubkey = (Pubkey) o.getPayload();
            if (Arrays.equals(address.getRipe(), pubkey.getRipe()))
                System.out.println("Pubkey found!");
            try {
                address.setPubkey(pubkey);
                System.out.println(address);
            } catch (Exception ignore) {
                System.out.println("But setPubkey failed? " + address.getRipe().length + "/" + pubkey.getRipe().length);
                System.out.println("Failed address: " + generateAddress(address.getStream(), address.getVersion(), pubkey.getRipe()));
                if (Arrays.equals(address.getRipe(), pubkey.getRipe())) {
                    ignore.printStackTrace();
                }
            }
        }
    }

    public static String generateAddress(long stream, long version, byte[] ripe) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Encode.varInt(version, os);
            Encode.varInt(stream, os);
            os.write(ripe);

            byte[] checksum = Security.doubleSha512(os.toByteArray());
            os.write(checksum, 0, 4);
            return "BM-" + Base58.encode(os.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
