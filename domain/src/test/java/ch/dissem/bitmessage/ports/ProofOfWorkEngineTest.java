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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Security;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Created by chris on 17.04.15.
 */
public class ProofOfWorkEngineTest {
    @Test
    public void testSimplePOWEngine() {
        testPOW(new SimplePOWEngine());
    }

    @Test
    public void testThreadedPOWEngine() {
        testPOW(new MultiThreadedPOWEngine());
    }

    private void testPOW(ProofOfWorkEngine engine) {
        long time = System.currentTimeMillis();
        byte[] initialHash = Security.sha512(new byte[]{1, 3, 6, 4});
        byte[] target = {0, 0, -1, -1, -1, -1, -1, -1};

        byte[] nonce = engine.calculateNonce(initialHash, target);
        System.out.println("Calculating nonce took " + (System.currentTimeMillis() - time) + "ms");
        assertTrue(Bytes.lt(Security.doubleSha512(nonce, initialHash), target, 8));
    }
}
