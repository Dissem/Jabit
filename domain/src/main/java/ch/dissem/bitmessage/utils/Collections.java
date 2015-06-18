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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class Collections {
    private final static Random RANDOM = new Random();

    /**
     * @param count      the number of elements to return (if possible)
     * @param collection the collection to take samples from
     * @return a random subset of the given collection, or a copy of the collection if it's not larger than count. The
     * result is by no means securely random, but should be random enough so not the same objects get selected over
     * and over again.
     */
    public static <T> List<T> selectRandom(int count, Collection<T> collection) {
        ArrayList<T> result = new ArrayList<>(count);
        if (collection.size() <= count) {
            result.addAll(collection);
        } else {
            double collectionRest = collection.size();
            double resultRest = count;
            int skipMax = (int) Math.ceil(collectionRest / resultRest);
            int skip = RANDOM.nextInt(skipMax);
            for (T item : collection) {
                collectionRest--;
                if (skip > 0) {
                    skip--;
                } else {
                    result.add(item);
                    resultRest--;
                    if (resultRest == 0) {
                        break;
                    }
                    skipMax = (int) Math.ceil(collectionRest / resultRest);
                    skip = RANDOM.nextInt(skipMax);
                }
            }
        }
        return result;
    }
}
