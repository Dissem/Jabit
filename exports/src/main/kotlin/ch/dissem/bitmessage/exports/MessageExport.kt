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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.utils.Base64
import ch.dissem.bitmessage.utils.Encode
import ch.dissem.bitmessage.utils.TTL
import com.beust.klaxon.*
import java.util.*

/**
 * Exports and imports messages and labels
 */
object MessageExport {
    fun exportLabels(labels: List<Label>) = json {
        array(
            labels.map {
                obj(
                    "label" to it.toString(),
                    "type" to it.type?.name,
                    "color" to it.color
                )
            }
        )
    }

    fun exportMessages(messages: List<Plaintext>) = json {
        array(messages.map {
            obj(
                "type" to it.type.name,
                "from" to it.from.address,
                "to" to it.to?.address,
                "subject" to it.subject,
                "body" to it.text,

                "conversationId" to it.conversationId.toString(),
                "msgId" to it.inventoryVector?.hash?.let { Base64.encodeToString(it) },
                "encoding" to it.encodingCode,
                "status" to it.status.name,
                "message" to Base64.encodeToString(it.message),
                "ackData" to it.ackData?.let { Base64.encodeToString(it) },
                "ackMessage" to it.ackMessage?.let { Base64.encodeToString(Encode.bytes(it)) },
                "signature" to it.signature?.let { Base64.encodeToString(it) },
                "sent" to it.sent,
                "received" to it.received,
                "ttl" to it.ttl,
                "labels" to array(it.labels.map { it.toString() })
            )
        })
    }

    fun importMessages(input: JsonArray<*>, labels: Map<String, Label>): List<Plaintext> {
        return input.filterIsInstance(JsonObject::class.java).map { json ->
            fun JsonObject.bytes(fieldName: String) = string(fieldName)?.let { Base64.decode(it) }
            Plaintext.Builder(Plaintext.Type.valueOf(json.string("type") ?: "MSG"))
                .from(json.string("from")?.let { BitmessageAddress(it) } ?: throw IllegalArgumentException("'from' address expected"))
                .to(json.string("to")?.let { BitmessageAddress(it) })
                .conversation(json.string("conversationId")?.let { UUID.fromString(it) } ?: UUID.randomUUID())
                .IV(json.bytes("msgId")?.let { InventoryVector(it) })
                .encoding(json.long("encoding") ?: throw IllegalArgumentException("encoding expected"))
                .status(json.string("status")?.let { Plaintext.Status.valueOf(it) } ?: throw IllegalArgumentException("status expected"))
                .message(json.bytes("message") ?: throw IllegalArgumentException("message expected"))
                .ackData(json.bytes("ackData"))
                .ackMessage(json.bytes("ackMessage"))
                .signature(json.bytes("signature"))
                .sent(json.long("sent"))
                .received(json.long("received"))
                .ttl(json.long("ttl") ?: TTL.msg)
                .labels(
                    json.array<String>("labels")?.map { labels[it] }?.filterNotNull() ?: emptyList()
                )
                .build()
        }
    }

    fun importLabels(input: JsonArray<Any?>): List<Label> {
        return input.filterIsInstance(JsonObject::class.java).map { json ->
            Label(
                label = json.string("label") ?: throw IllegalArgumentException("label expected"),
                type = json.string("type")?.let { Label.Type.valueOf(it) },
                color = json.int("color") ?: 0
            )
        }
    }

    fun createLabelMap(labels: List<Label>) = labels.associateBy { it.toString() }
}
