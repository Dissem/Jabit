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

package ch.dissem.bitmessage.utils

/**
 * Helper object to get a point from a public key on a elliptic curve.
 */
object Points {
    /**
     * returns X component of the point represented by public key P
     */
    @JvmStatic fun getX(P: ByteArray): ByteArray {
        return P.sliceArray(1..(P.size - 1) / 2)
    }

    /**
     * returns Y component of the point represented by public key P
     */
    @JvmStatic fun getY(P: ByteArray): ByteArray {
        return P.sliceArray((P.size - 1) / 2 + 1..P.size - 1)
    }
}
