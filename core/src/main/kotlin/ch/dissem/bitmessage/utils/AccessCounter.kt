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
 * Intended to count the bytes read or written during (de-)serialization.
 */
class AccessCounter {
    private var count: Int = 0

    /**
     * Increases the counter by one.
     */
    private fun inc() {
        count++
    }

    /**
     * Increases the counter by length.
     */
    private fun inc(length: Int) {
        count += length
    }

    fun length(): Int {
        return count
    }

    override fun toString(): String {
        return count.toString()
    }

    companion object {

        /**
         * Increases the counter by one, if not null.
         */
        @JvmStatic fun inc(counter: AccessCounter?) {
            counter?.inc()
        }

        /**
         * Increases the counter by length, if not null.
         */
        @JvmStatic fun inc(counter: AccessCounter?, length: Int) {
            counter?.inc(length)
        }
    }
}
