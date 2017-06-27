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
 * Stores times to live in seconds for different object types. Usually this shouldn't be messed with, but for tests
 * it might be a good idea to reduce it to a minimum, and on mobile clients you might want to optimize it as well.

 * @author Christian Basler
 */
object TTL {
    @JvmStatic var msg = 2 * UnixTime.DAY
        @JvmName("msg") get
        @JvmName("msg") set(msg) {
            field = validate(msg)
        }

    @JvmStatic var getpubkey = 2 * UnixTime.DAY
        @JvmName("getpubkey") get
        @JvmName("getpubkey") set(getpubkey) {
            field = validate(getpubkey)
        }

    @JvmStatic var pubkey = 28 * UnixTime.DAY
        @JvmName("pubkey") get
        @JvmName("pubkey") set(pubkey) {
            field = validate(pubkey)
        }

    private fun validate(ttl: Long): Long {
        if (ttl < 0 || ttl > 28 * UnixTime.DAY)
            throw IllegalArgumentException("TTL must be between 0 seconds and 28 days")
        return ttl
    }
}
