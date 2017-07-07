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

package ch.dissem.bitmessage.constants

/**
 * Some network constants
 */
object Network {
    @JvmField val NETWORK_MAGIC_NUMBER = 8
    @JvmField val HEADER_SIZE = 24
    @JvmField val MAX_PAYLOAD_SIZE = 1600003
    @JvmField val MAX_MESSAGE_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
}
