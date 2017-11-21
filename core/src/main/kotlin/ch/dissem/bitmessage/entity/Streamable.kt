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

import java.io.OutputStream
import java.io.Serializable
import java.nio.ByteBuffer

/**
 * An object that can be written to an [OutputStream]
 */
interface Streamable : Serializable {
    fun writer(): StreamableWriter
}

interface SignedStreamable : Streamable {
    override fun writer(): SignedStreamableWriter
}

interface EncryptedStreamable : SignedStreamable {
    override fun writer(): EncryptedStreamableWriter
}

interface StreamableWriter: Serializable {
    fun write(out: OutputStream)
    fun write(buffer: ByteBuffer)
}

interface SignedStreamableWriter : StreamableWriter {
    fun writeBytesToSign(out: OutputStream)
}

interface EncryptedStreamableWriter : SignedStreamableWriter {
    fun writeUnencrypted(out: OutputStream)
    fun writeUnencrypted(buffer: ByteBuffer)
}
