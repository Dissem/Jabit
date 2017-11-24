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
import ch.dissem.bitmessage.entity.SignedStreamableWriter
import ch.dissem.bitmessage.utils.Decode
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Request for a public key.
 */
class GetPubkey : ObjectPayload {

    override val type: ObjectType = ObjectType.GET_PUBKEY
    override val stream: Long

    /**
     * @return an array of bytes that represent either the ripe, or the tag of an address, depending on the
     * * address version.
     */
    val ripeTag: ByteArray

    constructor(address: BitmessageAddress) : super(address.version) {
        this.stream = address.stream
        this.ripeTag = if (address.version < 4) address.ripe else
            address.tag ?: throw IllegalStateException("Address of version 4 without tag shouldn't exist!")
    }

    private constructor(version: Long, stream: Long, ripeOrTag: ByteArray) : super(version) {
        this.stream = stream
        this.ripeTag = ripeOrTag
    }

    override fun writer(): SignedStreamableWriter = Writer(this)

    private class Writer(
        private val item: GetPubkey
    ) : SignedStreamableWriter {

        override fun write(out: OutputStream) {
            out.write(item.ripeTag)
        }

        override fun write(buffer: ByteBuffer) {
            buffer.put(item.ripeTag)
        }

        override fun writeBytesToSign(out: OutputStream) = Unit // nothing to sign

    }

    companion object {
        @JvmStatic
        fun read(input: InputStream, stream: Long, length: Int, version: Long): GetPubkey {
            return GetPubkey(version, stream, Decode.bytes(input, length))
        }
    }
}
