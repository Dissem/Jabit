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
import java.nio.ByteBuffer

/**
 * The 'verack' command answers a 'version' command, accepting the other node's version.
 */
class VerAck : MessagePayload {

    override val command: MessagePayload.Command = MessagePayload.Command.VERACK

    // 'verack' doesn't have any payload, so there is nothing to write
    override fun writer(): StreamableWriter = EmptyWriter

    internal object EmptyWriter : StreamableWriter {
        override fun write(out: OutputStream) = Unit
        override fun write(buffer: ByteBuffer) = Unit
    }

    companion object {
        private val serialVersionUID = -4302074845199181687L
    }
}
