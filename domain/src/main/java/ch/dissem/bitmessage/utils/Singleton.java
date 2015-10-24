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

import ch.dissem.bitmessage.ports.Security;

/**
 * Created by chris on 20.07.15.
 */
public class Singleton {
    private static Security security;

    public static void initialize(Security security) {
        synchronized (Singleton.class) {
            if (Singleton.security == null) {
                Singleton.security = security;
            }
        }
    }

    public static Security security() {
        return security;
    }
}
