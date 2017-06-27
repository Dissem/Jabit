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
 * Waits for a value within a callback method to be set.
 */
class CallbackWaiter<T> {
    private val startTime = System.currentTimeMillis()
    @Volatile private var isSet: Boolean = false
    private var _value: T? = null
    var time: Long = 0
        private set

    fun setValue(value: T?) {
        synchronized(this) {
            this.time = System.currentTimeMillis() - startTime
            this._value = value
            this.isSet = true
        }
    }

    fun waitForValue(): T? {
        while (!isSet) {
            Thread.sleep(100)
        }
        synchronized(this) {
            return _value
        }
    }
}
