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
import ch.dissem.bitmessage.utils.CallbackWaiter;
import ch.dissem.bitmessage.utils.TestBase;
import org.junit.Test;

import static ch.dissem.bitmessage.utils.Singleton.security;
import static org.junit.Assert.assertTrue;

public class ProofOfWorkEngineTest extends TestBase {
    @Test(timeout = 90_000)
    public void testSimplePOWEngine() throws InterruptedException {
        testPOW(new SimplePOWEngine());
    }

    @Test(timeout = 90_000)
    public void testThreadedPOWEngine() throws InterruptedException {
        testPOW(new MultiThreadedPOWEngine());
    }

    private void testPOW(ProofOfWorkEngine engine) throws InterruptedException {
        byte[] initialHash = security().sha512(new byte[]{1, 3, 6, 4});
        byte[] target = {0, 0, 0, -1, -1, -1, -1, -1};

        final CallbackWaiter<byte[]> waiter1 = new CallbackWaiter<>();
        engine.calculateNonce(initialHash, target,
                new ProofOfWorkEngine.Callback() {
                    @Override
                    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
                        waiter1.setValue(nonce);
                    }
                });
        byte[] nonce = waiter1.waitForValue();
        System.out.println("Calculating nonce took " + waiter1.getTime() + "ms");
        assertTrue(Bytes.lt(security().doubleSha512(nonce, initialHash), target, 8));

        // Let's add a second (shorter) run to find possible multi threading issues
        byte[] initialHash2 = security().sha512(new byte[]{1, 3, 6, 5});
        byte[] target2 = {0, 0, -1, -1, -1, -1, -1, -1};

        final CallbackWaiter<byte[]> waiter2 = new CallbackWaiter<>();
        engine.calculateNonce(initialHash2, target2,
                new ProofOfWorkEngine.Callback() {
                    @Override
                    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
                        waiter2.setValue(nonce);
                    }
                });
        byte[] nonce2 = waiter2.waitForValue();
        System.out.println("Calculating nonce took " + waiter2.getTime() + "ms");
        assertTrue(Bytes.lt(security().doubleSha512(nonce2, initialHash2), target2, 8));
        assertTrue("Second nonce must be quicker to find", waiter1.getTime() > waiter2.getTime());
    }

}
