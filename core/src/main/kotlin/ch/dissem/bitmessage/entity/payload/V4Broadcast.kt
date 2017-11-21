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

package ch.dissem.bitmessage.entity.payload

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.SignedStreamableWriter
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 * Broadcasts are version 4 or 5.
 */
open class V4Broadcast : Broadcast {

    override val type: ObjectType = ObjectType.BROADCAST

    protected constructor(version: Long, stream: Long, encrypted: CryptoBox?, plaintext: Plaintext?) : super(version, stream, encrypted, plaintext)

    constructor(senderAddress: BitmessageAddress, plaintext: Plaintext) : super(4, senderAddress.stream, null, plaintext) {
        if (senderAddress.version >= 4)
            throw IllegalArgumentException("Address version 3 or older expected, but was " + senderAddress.version)
    }

    override fun writer(): SignedStreamableWriter = Writer(this)

    protected open class Writer(
        private val item: V4Broadcast
    ) : SignedStreamableWriter {

        override fun writeBytesToSign(out: OutputStream) {
            item.plaintext?.writer(false)?.write(out) ?: throw IllegalStateException("no plaintext data available")
        }

        override fun write(out: OutputStream) {
            item.encrypted?.writer()?.write(out) ?: throw IllegalStateException("broadcast not encrypted")
        }

        override fun write(buffer: ByteBuffer) {
            item.encrypted?.writer()?.write(buffer) ?: throw IllegalStateException("broadcast not encrypted")
        }
    }

    companion object {
        @JvmStatic
        fun read(`in`: InputStream, stream: Long, length: Int): V4Broadcast {
            return V4Broadcast(4, stream, CryptoBox.read(`in`, length), null)
        }
    }
}
