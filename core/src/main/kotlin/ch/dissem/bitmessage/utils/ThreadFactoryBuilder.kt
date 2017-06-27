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

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class ThreadFactoryBuilder private constructor(pool: String) {
    private val namePrefix: String = pool + "-thread-"
    private var prio = Thread.NORM_PRIORITY
    private var daemon = false

    fun lowPrio(): ThreadFactoryBuilder {
        prio = Thread.MIN_PRIORITY
        return this
    }

    fun daemon(): ThreadFactoryBuilder {
        daemon = true
        return this
    }

    fun build(): ThreadFactory {
        val s = System.getSecurityManager()
        val group = if (s != null)
            s.threadGroup
        else
            Thread.currentThread().threadGroup

        return object : ThreadFactory {
            private val threadNumber = AtomicInteger(1)

            override fun newThread(r: Runnable): Thread {
                val t = Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0)
                t.priority = prio
                t.isDaemon = daemon
                return t
            }
        }
    }

    companion object {
        @JvmStatic fun pool(name: String): ThreadFactoryBuilder {
            return ThreadFactoryBuilder(name)
        }
    }
}
