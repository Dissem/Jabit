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

package ch.dissem.bitmessage.extensions;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.CustomMessage;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.extensions.pow.ProofOfWorkRequest;
import ch.dissem.bitmessage.utils.TestBase;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static ch.dissem.bitmessage.utils.Singleton.security;
import static org.junit.Assert.assertEquals;

public class CryptoCustomMessageTest extends TestBase {
    @Test
    public void ensureEncryptThenDecryptYieldsSameObject() throws Exception {
        PrivateKey privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"));
        BitmessageAddress sendingIdentity = new BitmessageAddress(privateKey);

        GenericPayload payloadBefore = new GenericPayload(0, 1, security().randomBytes(100));
        CryptoCustomMessage<GenericPayload> messageBefore = new CryptoCustomMessage<>(payloadBefore);
        messageBefore.signAndEncrypt(sendingIdentity, security().createPublicKey(sendingIdentity.getPublicDecryptionKey()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageBefore.write(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        CustomMessage customMessage = CustomMessage.read(in, out.size());
        CryptoCustomMessage<GenericPayload> messageAfter = CryptoCustomMessage.read(customMessage,
                new CryptoCustomMessage.Reader<GenericPayload>() {
                    @Override
                    public GenericPayload read(BitmessageAddress ignore, InputStream in) throws IOException {
                        return GenericPayload.read(0, 1, in, 100);
                    }
                });
        GenericPayload payloadAfter = messageAfter.decrypt(sendingIdentity.getPublicDecryptionKey());

        assertEquals(payloadBefore, payloadAfter);
    }

    @Test
    public void testWithActualRequest() throws Exception {
        PrivateKey privateKey = PrivateKey.read(TestUtils.getResource("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8.privkey"));
        final BitmessageAddress sendingIdentity = new BitmessageAddress(privateKey);

        ProofOfWorkRequest requestBefore = new ProofOfWorkRequest(sendingIdentity, security().randomBytes(64),
                ProofOfWorkRequest.Request.CALCULATE);

        CryptoCustomMessage<ProofOfWorkRequest> messageBefore = new CryptoCustomMessage<>(requestBefore);
        messageBefore.signAndEncrypt(sendingIdentity, security().createPublicKey(sendingIdentity.getPublicDecryptionKey()));


        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageBefore.write(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        CustomMessage customMessage = CustomMessage.read(in, out.size());
        CryptoCustomMessage<ProofOfWorkRequest> messageAfter = CryptoCustomMessage.read(customMessage,
                new ProofOfWorkRequest.Reader(sendingIdentity));
        ProofOfWorkRequest requestAfter = messageAfter.decrypt(sendingIdentity.getPublicDecryptionKey());

        assertEquals(requestBefore, requestAfter);
    }
}
