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

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.entity.valueobject.NetworkAddress
import ch.dissem.bitmessage.utils.Encode
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * The 'addr' command holds a list of known active Bitmessage nodes.
 */
data class Addr constructor(val addresses: List<NetworkAddress>) : MessagePayload {
    override val command: MessagePayload.Command = MessagePayload.Command.ADDR

    override fun write(out: OutputStream) {
        Encode.varInt(addresses.size.toLong(), out)
        for (address in addresses) {
            address.write(out)
        }
    }

    override fun write(buffer: ByteBuffer) {
        Encode.varInt(addresses.size.toLong(), buffer)
        for (address in addresses) {
            address.write(buffer)
        }
    }
}
