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

package ch.dissem.bitmessage.utils;

/**
 * Waits for a value within a callback method to be set.
 */
public class CallbackWaiter<T> {
    private final long startTime = System.currentTimeMillis();
    private volatile boolean isSet;
    private T value;
    private long time;

    public void setValue(T value) {
        synchronized (this) {
            this.time = System.currentTimeMillis() - startTime;
            this.value = value;
            this.isSet = true;
        }
    }

    public T waitForValue() throws InterruptedException {
        while (!isSet) {
            Thread.sleep(100);
        }
        synchronized (this) {
            return value;
        }
    }

    public long getTime() {
        return time;
    }
}
