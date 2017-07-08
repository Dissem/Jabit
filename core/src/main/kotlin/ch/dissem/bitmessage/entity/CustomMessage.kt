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

import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.utils.AccessCounter
import ch.dissem.bitmessage.utils.Decode.bytes
import ch.dissem.bitmessage.utils.Decode.varString
import ch.dissem.bitmessage.utils.Encode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * @author Christian Basler
 */
open class CustomMessage(val customCommand: String, private val data: ByteArray? = null) : MessagePayload {

    override val command: MessagePayload.Command = MessagePayload.Command.CUSTOM

    val isError: Boolean

    fun getData(): ByteArray {
        if (data != null) {
            return data
        } else {
            val out = ByteArrayOutputStream()
            write(out)
            return out.toByteArray()
        }
    }

    override fun write(out: OutputStream) {
        if (data != null) {
            Encode.varString(customCommand, out)
            out.write(data)
        } else {
            throw ApplicationException("Tried to write custom message without data. "
                + "Programmer: did you forget to override #write()?")
        }
    }

    override fun write(buffer: ByteBuffer) {
        if (data != null) {
            Encode.varString(customCommand, buffer)
            buffer.put(data)
        } else {
            throw ApplicationException("Tried to write custom message without data. "
                + "Programmer: did you forget to override #write()?")
        }
    }

    companion object {
        val COMMAND_ERROR = "ERROR"

        @JvmStatic
        fun read(`in`: InputStream, length: Int): CustomMessage {
            val counter = AccessCounter()
            return CustomMessage(varString(`in`, counter), bytes(`in`, length - counter.length()))
        }

        @JvmStatic
        fun error(message: String): CustomMessage {
            return CustomMessage(COMMAND_ERROR, message.toByteArray(charset("UTF-8")))
        }
    }

    init {
        this.isError = COMMAND_ERROR == customCommand
    }
}
