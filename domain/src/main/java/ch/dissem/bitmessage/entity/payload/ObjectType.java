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

package ch.dissem.bitmessage.entity.payload;

/**
 * Known types for 'object' messages. Must not be used where an unknown type must be resent.
 */
public enum ObjectType {
    GET_PUBKEY(0),
    PUBKEY(1),
    MSG(2),
    BROADCAST(3);

    int number;

    ObjectType(int number) {
        this.number = number;
    }

    public static ObjectType fromNumber(long number) {
        for (ObjectType type : values()) {
            if (type.number == number) return type;
        }
        return null;
    }

    public long getNumber() {
        return number;
    }
}
