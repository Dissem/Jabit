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
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class SignatureTest {
    @Test
    public void ensureValidationWorks() throws IOException {
        ObjectMessage object = TestUtils.loadObjectMessage(3, "V3Pubkey.payload");
        Pubkey pubkey = (Pubkey) object.getPayload();
        assertTrue(object.isSignatureValid(pubkey));
    }

    @Test
    public void ensureSigningWorks() throws IOException {
        PrivateKey privateKey = new PrivateKey(1, 1000, 1000);
        BitmessageAddress address = new BitmessageAddress(privateKey);

        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .objectType(ObjectType.PUBKEY)
                .stream(1)
                .version(1)
                .payload(privateKey.getPubkey())
                .build();
        objectMessage.sign(privateKey);

        assertTrue(objectMessage.isSignatureValid(privateKey.getPubkey()));
    }
}
