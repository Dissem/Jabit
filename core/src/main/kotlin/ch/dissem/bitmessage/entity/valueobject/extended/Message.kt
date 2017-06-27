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

package ch.dissem.bitmessage.entity.valueobject.extended

import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.utils.Strings.str
import ch.dissem.msgpack.types.*
import ch.dissem.msgpack.types.Utils.mp
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLConnection
import java.nio.file.Files
import java.util.*

/**
 * Extended encoding type 'message'. Properties 'parents' and 'files' not yet supported by PyBitmessage, so they might not work
 * properly with future PyBitmessage implementations.
 */
data class Message constructor(
    val subject: String,
    val body: String,
    val parents: List<InventoryVector>,
    val files: List<Attachment>
) : ExtendedEncoding.ExtendedType {

    override val type: String = TYPE

    override fun pack(): MPMap<MPString, MPType<*>> {
        val result = MPMap<MPString, MPType<*>>()
        result.put(mp(""), mp(TYPE))
        result.put(mp("subject"), mp(subject))
        result.put(mp("body"), mp(body))

        if (!files.isEmpty()) {
            val items = MPArray<MPMap<MPString, MPType<*>>>()
            result.put(mp("files"), items)
            for (file in files) {
                val item = MPMap<MPString, MPType<*>>()
                item.put(mp("name"), mp(file.name))
                item.put(mp("data"), mp(*file.data))
                item.put(mp("type"), mp(file.type))
                item.put(mp("disposition"), mp(file.disposition.name))
                items.add(item)
            }
        }
        if (!parents.isEmpty()) {
            val items = MPArray<MPBinary>()
            result.put(mp("parents"), items)
            for ((hash) in parents) {
                items.add(mp(*hash))
            }
        }
        return result
    }

    class Builder {
        private var subject: String? = null
        private var body: String? = null
        private val parents = LinkedList<InventoryVector>()
        private val files = LinkedList<Attachment>()

        fun subject(subject: String): Builder {
            this.subject = subject
            return this
        }

        fun body(body: String): Builder {
            this.body = body
            return this
        }

        fun addParent(parent: Plaintext?): Builder {
            if (parent != null) {
                val iv = parent.inventoryVector
                if (iv == null) {
                    LOG.debug("Ignored parent without IV")
                } else {
                    parents.add(iv)
                }
            }
            return this
        }

        fun addParent(iv: InventoryVector?): Builder {
            if (iv != null) {
                parents.add(iv)
            }
            return this
        }

        fun addFile(file: File?, disposition: Attachment.Disposition): Builder {
            if (file != null) {
                try {
                    files.add(Attachment.Builder()
                        .name(file.name)
                        .disposition(disposition)
                        .type(URLConnection.guessContentTypeFromStream(FileInputStream(file)))
                        .data(Files.readAllBytes(file.toPath()))
                        .build())
                } catch (e: IOException) {
                    LOG.error(e.message, e)
                }

            }
            return this
        }

        fun addFile(file: Attachment?): Builder {
            if (file != null) {
                files.add(file)
            }
            return this
        }

        fun build(): ExtendedEncoding {
            return ExtendedEncoding(Message(subject!!, body!!, parents, files))
        }
    }

    class Unpacker : ExtendedEncoding.Unpacker<Message> {
        override val type: String = TYPE

        override fun unpack(map: MPMap<MPString, MPType<*>>): Message {
            val subject = str(map[mp("subject")]) ?: ""
            val body = str(map[mp("body")]) ?: ""
            val parents = LinkedList<InventoryVector>()
            val files = LinkedList<Attachment>()
            val mpParents = map[mp("parents")] as? MPArray<*>
            for (parent in mpParents ?: emptyList<MPArray<MPBinary>>()) {
                parents.add(InventoryVector.fromHash(
                    (parent as? MPBinary)?.value ?: continue
                ) ?: continue)
            }
            val mpFiles = map[mp("files")] as? MPArray<*>
            for (item in mpFiles ?: emptyList<Any>()) {
                if (item is MPMap<*, *>) {
                    val b = Attachment.Builder()
                    b.name(str(item[mp("name")])!!)
                    b.data(
                        bin(item[mp("data")] ?: continue) ?: continue
                    )
                    b.type(str(item[mp("type")])!!)
                    val disposition = str(item[mp("disposition")])
                    if ("inline" == disposition) {
                        b.inline()
                    } else if ("attachment" == disposition) {
                        b.attachment()
                    }
                    files.add(b.build())
                }
            }

            return Message(subject, body, parents, files)
        }

        private fun bin(data: MPType<*>): ByteArray? {
            return (data as? MPBinary)?.value
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Message::class.java)

        val TYPE = "message"
    }
}
