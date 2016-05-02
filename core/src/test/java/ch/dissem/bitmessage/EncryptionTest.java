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
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.CryptoBox;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.utils.TestBase;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.IOException;

import static ch.dissem.bitmessage.utils.Singleton.security;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EncryptionTest extends TestBase {
    @Test
    public void ensureDecryptedDataIsSameAsBeforeEncryption() throws IOException, DecryptionFailedException {
        GenericPayload before = new GenericPayload(0, 1, security().randomBytes(100));

        PrivateKey privateKey = new PrivateKey(false, 1, 1000, 1000);
        CryptoBox cryptoBox = new CryptoBox(before, privateKey.getPubkey().getEncryptionKey());

        GenericPayload after = GenericPayload.read(0, 1, cryptoBox.decrypt(privateKey.getPrivateEncryptionKey()), 100);

        assertEquals(before, after);
    }

    @Test
    public void ensureMessageCanBeDecrypted() throws IOException, DecryptionFailedException {
        PrivateKey privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"));
        BitmessageAddress identity = new BitmessageAddress(privateKey);
        assertEquals("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8", identity.getAddress());

        ObjectMessage object = TestUtils.loadObjectMessage(3, "V1Msg.payload");
        Msg msg = (Msg) object.getPayload();
        msg.decrypt(privateKey.getPrivateEncryptionKey());
        Plaintext plaintext = msg.getPlaintext();
        assertNotNull(plaintext);
        assertEquals("Test", plaintext.getSubject());
        assertEquals("Hallo, das ist ein Test von der v4-Adresse", plaintext.getText());
    }
}
