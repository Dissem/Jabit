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

package ch.dissem.bitmessage.networking;

import ch.dissem.bitmessage.entity.NetworkMessage;
import ch.dissem.bitmessage.entity.Version;
import ch.dissem.bitmessage.entity.valueobject.NetworkAddress;
import ch.dissem.bitmessage.utils.UnixTime;
import org.junit.Test;

/**
 * Created by chris on 20.03.15.
 */
public class NetworkNodeTest {
    private NetworkAddress localhost = new NetworkAddress.Builder().ipv4(127, 0, 0, 1).port(8444).build();

    @Test(expected = InterruptedException.class)
    public void testSendMessage() throws Exception {
        final Thread baseThread = Thread.currentThread();
        NetworkNode net = new NetworkNode();
//        net.setListener(localhost, new NetworkHandler.MessageListener() {
//            @Override
//            public void receive(ObjectPayload payload) {
//                System.out.println(payload);
//                baseThread.interrupt();
//            }
//        });
        NetworkMessage ver = new NetworkMessage(
                new Version.Builder()
                        .version(3)
                        .services(1)
                        .timestamp(UnixTime.now())
                        .addrFrom(localhost)
                        .addrRecv(localhost)
                        .nonce(-1)
                        .userAgent("Test")
                        .streams(1, 2)
                        .build()
        );
//        net.send(localhost, ver);
        Thread.sleep(20000);
    }
}
