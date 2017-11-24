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

package ch.dissem.bitmessage.exports

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.Base64
import ch.dissem.bitmessage.utils.Encode
import com.beust.klaxon.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Exports and imports contacts and identities
 */
object ContactExport {
    fun exportContacts(contacts: List<BitmessageAddress>, includePrivateKey: Boolean = false) = json {
        array(
            contacts.map {
                obj(
                    "alias" to it.alias,
                    "address" to it.address,
                    "chan" to it.isChan,
                    "subscribed" to it.isSubscribed,
                    "pubkey" to it.pubkey?.let {
                        val out = ByteArrayOutputStream()
                        it.writer().writeUnencrypted(out)
                        Base64.encodeToString(out.toByteArray())
                    },
                    "privateKey" to if (includePrivateKey) {
                        it.privateKey?.let { Base64.encodeToString(Encode.bytes(it)) }
                    } else {
                        null
                    }
                )
            }
        )
    }

    fun importContacts(input: JsonArray<*>): List<BitmessageAddress> {
        return input.filterIsInstance(JsonObject::class.java).map { json ->
            fun JsonObject.bytes(fieldName: String) = string(fieldName)?.let { Base64.decode(it) }
            val privateKey = json.bytes("privateKey")?.let { PrivateKey.read(ByteArrayInputStream(it)) }
            if (privateKey != null) {
                BitmessageAddress(privateKey)
            } else {
                BitmessageAddress(json.string("address") ?: throw IllegalArgumentException("address expected"))
            }.apply {
                alias = json.string("alias")
                isChan = json.boolean("chan") ?: false
                isSubscribed = json.boolean("subscribed") ?: false
                pubkey = json.bytes("pubkey")?.let {
                    Factory.readPubkey(
                        version = version,
                        stream = stream,
                        input = ByteArrayInputStream(it),
                        length = it.size,
                        encrypted = false
                    )
                }
            }
        }
    }
}
