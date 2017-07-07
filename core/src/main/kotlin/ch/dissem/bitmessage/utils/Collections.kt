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

import java.util.*

object Collections {
    private val RANDOM = Random()

    /**
     * @param count      the number of elements to return (if possible)
     * *
     * @param collection the collection to take samples from
     * *
     * @return a random subset of the given collection, or a copy of the collection if it's not larger than count. The
     * * result is by no means securely random, but should be random enough so not the same objects get selected over
     * * and over again.
     */
    @JvmStatic fun <T> selectRandom(count: Int, collection: Collection<T>): List<T> {
        val result = ArrayList<T>(count)
        if (collection.size <= count) {
            result.addAll(collection)
        } else {
            var collectionRest = collection.size.toDouble()
            var resultRest = count.toDouble()
            var skipMax = Math.ceil(collectionRest / resultRest).toInt()
            var skip = RANDOM.nextInt(skipMax)
            for (item in collection) {
                collectionRest--
                if (skip > 0) {
                    skip--
                } else {
                    result.add(item)
                    resultRest--
                    if (resultRest == 0.0) {
                        break
                    }
                    skipMax = Math.ceil(collectionRest / resultRest).toInt()
                    skip = RANDOM.nextInt(skipMax)
                }
            }
        }
        return result
    }

    @JvmStatic fun <T> selectRandom(collection: Collection<T>): T {
        var index = RANDOM.nextInt(collection.size)
        for (item in collection) {
            if (index == 0) {
                return item
            }
            index--
        }
        throw IllegalArgumentException("Empty collection? Size: " + collection.size)
    }
}
