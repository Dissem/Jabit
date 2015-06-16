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

import ch.dissem.bitmessage.entity.ObjectMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class DebugUtils {
    private final static Logger LOG = LoggerFactory.getLogger(DebugUtils.class);

    public static void saveToFile(ObjectMessage objectMessage) {
        try {
            File f = new File(System.getProperty("user.home") + "/jabit.error/" + objectMessage.getInventoryVector() + ".inv");
            f.createNewFile();
            objectMessage.write(new FileOutputStream(f));
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
        }
    }

    public static <K> void inc(Map<K, Integer> map, K key) {
        if (map.containsKey(key)) {
            map.put(key, map.get(key) + 1);
        } else {
            map.put(key, 1);
        }
    }
}
