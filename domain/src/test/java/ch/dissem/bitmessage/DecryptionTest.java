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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.V4Broadcast;
import ch.dissem.bitmessage.entity.payload.V5Broadcast;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DecryptionTest {
    @Test
    public void ensureV4BroadcastIsDecryptedCorrectly() throws IOException, DecryptionFailedException {
        ObjectMessage objectMessage = TestUtils.loadObjectMessage(5, "V4Broadcast.payload");
        V4Broadcast broadcast = (V4Broadcast) objectMessage.getPayload();
        broadcast.decrypt(new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ"));
        assertEquals("Test-Broadcast", broadcast.getPlaintext().getSubject());
    }

    @Test
    public void ensureV5BroadcastIsDecryptedCorrectly() throws IOException, DecryptionFailedException {
        ObjectMessage objectMessage = TestUtils.loadObjectMessage(5, "V5Broadcast.payload");
        V5Broadcast broadcast = (V5Broadcast) objectMessage.getPayload();
        broadcast.decrypt(new BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h"));
        assertEquals("Test-Broadcast", broadcast.getPlaintext().getSubject());
    }
}
