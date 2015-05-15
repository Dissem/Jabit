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

import ch.dissem.bitmessage.entity.payload.CryptoBox;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.Security;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;

import static org.junit.Assert.assertEquals;

/**
 * Created by chris on 10.05.15.
 */
public class EncryptionTest {
    @Test
    public void ensureDecryptedDataIsSameAsBeforeEncryption() throws IOException {
        GenericPayload before = new GenericPayload(1, Security.randomBytes(100));

        PrivateKey privateKey = new PrivateKey(1, 1000, 1000);
        CryptoBox cryptoBox = new CryptoBox(before, privateKey.getPubkey().getEncryptionKey());

        GenericPayload after = GenericPayload.read(cryptoBox.decrypt(privateKey.getPrivateEncryptionKey()), 1, 100);

        assertEquals(before, after);
    }
}
