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

/**
 * Does the proof of work necessary to send an object.
 */
interface ProofOfWorkEngine {
    /**
     * Returns a nonce, such that the first 8 bytes from sha512(sha512(nonce||initialHash)) represent a unsigned long
     * smaller than target.

     * @param initialHash the SHA-512 hash of the object to send, sans nonce
     * *
     * @param target      the target, representing an unsigned long
     * *
     * @param callback    called with the calculated nonce as argument. The ProofOfWorkEngine implementation must make
     * *                    sure this is only called once.
     */
    fun calculateNonce(initialHash: ByteArray, target: ByteArray, callback: Callback)

    interface Callback {
        /**
         * @param nonce 8 bytes nonce
         */
        fun onNonceCalculated(initialHash: ByteArray, nonce: ByteArray)
    }
}
