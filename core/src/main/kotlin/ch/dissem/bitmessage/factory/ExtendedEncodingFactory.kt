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

package ch.dissem.bitmessage.factory

import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.entity.valueobject.extended.Vote
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.utils.Strings.str
import ch.dissem.msgpack.Reader
import ch.dissem.msgpack.types.MPMap
import ch.dissem.msgpack.types.MPString
import ch.dissem.msgpack.types.MPType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.*
import java.util.zip.InflaterInputStream

/**
 * Factory that creates [ExtendedEncoding] objects from byte arrays. You can register your own types by adding a
 * [ExtendedEncoding.Unpacker] using [.registerFactory].
 */
object ExtendedEncodingFactory {

    private val LOG = LoggerFactory.getLogger(ExtendedEncodingFactory::class.java)
    private val KEY_MESSAGE_TYPE = MPString("")

    private val factories = HashMap<String, ExtendedEncoding.Unpacker<*>>()

    init {
        registerFactory(Message.Unpacker())
        registerFactory(Vote.Unpacker())
    }

    fun registerFactory(factory: ExtendedEncoding.Unpacker<*>) {
        factories.put(factory.type, factory)
    }


    fun unzip(zippedData: ByteArray): ExtendedEncoding? {
        try {
            InflaterInputStream(ByteArrayInputStream(zippedData)).use { unzipper ->
                val reader = Reader.getInstance()
                @Suppress("UNCHECKED_CAST")
                val map = reader.read(unzipper) as MPMap<MPString, MPType<*>>
                val messageType = map[KEY_MESSAGE_TYPE]
                if (messageType == null) {
                    LOG.error("Missing message type")
                    return null
                }
                val factory = factories[str(messageType)]
                return ExtendedEncoding(
                    factory?.unpack(map) ?: return null
                )
            }
        } catch (e: ClassCastException) {
            throw ApplicationException(e)
        }
    }
}
