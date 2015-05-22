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
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.repository.JdbcAddressRepository;
import ch.dissem.bitmessage.repository.JdbcInventory;
import ch.dissem.bitmessage.repository.JdbcMessageRepository;
import ch.dissem.bitmessage.repository.JdbcNodeRegistry;
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Encode;
import ch.dissem.bitmessage.utils.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;

/**
 * Created by chris on 06.04.15.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        final BitmessageAddress address = new BitmessageAddress("BM-87hJ99tPAXxtetvnje7Z491YSvbEtBJVc5e");

        new Application();
//
//
//        List<ObjectMessage> objects = new JdbcInventory().getObjects(address.getStream(), address.getVersion(), ObjectType.PUBKEY);
//        System.out.println("Address version: " + address.getVersion());
//        System.out.println("Address stream:  " + address.getStream());
//        for (ObjectMessage o : objects) {
////            if (!o.isSignatureValid()) System.out.println("Invalid signature.");
////            System.out.println(o.getPayload().getSignature().length);
//            V4Pubkey pubkey = (V4Pubkey) o.getPayload();
//            if (Arrays.equals(address.getTag(), pubkey.getTag())) {
//                System.out.println("Pubkey found!");
//                try {
//                    System.out.println("IV: " + o.getInventoryVector());
//                    address.setPubkey(pubkey);
//                } catch (Exception ignore) {
//                    System.out.println("But setPubkey failed? " + address.getRipe().length + "/" + pubkey.getRipe().length);
//                    System.out.println("Failed address: " + generateAddress(address.getStream(), address.getVersion(), pubkey.getRipe()));
//                    if (Arrays.equals(address.getRipe(), pubkey.getRipe())) {
//                        ignore.printStackTrace();
//                    }
//                }
//            }
//        }
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
