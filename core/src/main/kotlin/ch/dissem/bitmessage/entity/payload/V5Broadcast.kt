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
import ch.dissem.bitmessage.utils.Decode

import java.io.InputStream
import java.io.OutputStream

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 */
class V5Broadcast : V4Broadcast {

    val tag: ByteArray

    private constructor(stream: Long, tag: ByteArray, encrypted: CryptoBox) : super(5, stream, encrypted, null) {
        this.tag = tag
    }

    constructor(senderAddress: BitmessageAddress, plaintext: Plaintext) : super(5, senderAddress.stream, null, plaintext) {
        if (senderAddress.version < 4)
            throw IllegalArgumentException("Address version 4 (or newer) expected, but was " + senderAddress.version)
        this.tag = senderAddress.tag ?: throw IllegalStateException("version 4 address without tag")
    }

    override fun writeBytesToSign(out: OutputStream) {
        out.write(tag)
        super.writeBytesToSign(out)
    }

    override fun write(out: OutputStream) {
        out.write(tag)
        super.write(out)
    }

    companion object {
        @JvmStatic fun read(`is`: InputStream, stream: Long, length: Int): V5Broadcast {
            return V5Broadcast(stream, Decode.bytes(`is`, 32), CryptoBox.read(`is`, length - 32))
        }
    }
}
