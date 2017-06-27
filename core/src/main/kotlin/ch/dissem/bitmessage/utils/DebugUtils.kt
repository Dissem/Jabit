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

import ch.dissem.bitmessage.entity.ObjectMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object DebugUtils {
    private val LOG = LoggerFactory.getLogger(DebugUtils::class.java)

    @JvmStatic fun saveToFile(objectMessage: ObjectMessage) {
        try {
            val f = File(System.getProperty("user.home") + "/jabit.error/" + objectMessage.inventoryVector + ".inv")
            f.createNewFile()
            objectMessage.write(FileOutputStream(f))
        } catch (e: IOException) {
            LOG.debug(e.message, e)
        }

    }

    @JvmStatic fun <K> inc(map: MutableMap<K, Int>, key: K) {
        val value = map[key]
        if (value == null) {
            map.put(key, 1)
        } else {
            map.put(key, value + 1)
        }
    }
}
