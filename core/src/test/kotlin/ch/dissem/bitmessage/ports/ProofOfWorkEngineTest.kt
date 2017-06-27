/*
 * Copyright 2017 Christian Basler
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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.CallbackWaiter
import ch.dissem.bitmessage.utils.Singleton.cryptography
import ch.dissem.bitmessage.utils.TestBase
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofOfWorkEngineTest : TestBase() {
    @Test(timeout = 90000)
    fun `test SimplePOWEngine`() {
        testPOW(SimplePOWEngine())
    }

    @Test(timeout = 90000)
    fun `test MultiThreadedPOWEngine`() {
        testPOW(MultiThreadedPOWEngine())
    }

    private fun testPOW(engine: ProofOfWorkEngine) {
        val initialHash = cryptography().sha512(byteArrayOf(1, 3, 6, 4))
        val target = byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1)

        val waiter1 = CallbackWaiter<ByteArray>()
        engine.calculateNonce(initialHash, target,
            object : ProofOfWorkEngine.Callback {
                @Suppress("NAME_SHADOWING")
                override fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray) {
                    waiter1.setValue(nonce)
                }
            })
        val nonce1 = waiter1.waitForValue()!!
        println("Calculating nonce1 took ${waiter1.time}ms")
        assertTrue(Bytes.lt(cryptography().doubleSha512(nonce1, initialHash), target, 8))

        // Let's add a second (shorter) run to find possible multi threading issues
        val initialHash2 = cryptography().sha512(byteArrayOf(1, 3, 6, 5))
        val target2 = byteArrayOf(0, 0, -1, -1, -1, -1, -1, -1)

        val waiter2 = CallbackWaiter<ByteArray>()
        engine.calculateNonce(initialHash2, target2,
            object : ProofOfWorkEngine.Callback {
                @Suppress("NAME_SHADOWING")
                override fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray) {
                    waiter2.setValue(nonce)
                }
            })
        val nonce2 = waiter2.waitForValue()!!
        println("Calculating nonce1 took ${waiter2.time}ms")
        assertTrue(Bytes.lt(cryptography().doubleSha512(nonce2, initialHash2), target2, 8))
        assertTrue("Second nonce1 must be quicker to find", waiter1.time > waiter2.time)
    }
}
