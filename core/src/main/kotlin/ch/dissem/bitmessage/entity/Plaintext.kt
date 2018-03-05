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

import ch.dissem.bitmessage.entity.Plaintext.Encoding.*
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.Msg
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.entity.valueobject.extended.Attachment
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.factory.ExtendedEncodingFactory
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.*
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.io.*
import java.nio.ByteBuffer
import java.util.*
import java.util.Collections
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

private fun message(encoding: Plaintext.Encoding, subject: String, body: String): ByteArray = when (encoding) {
    SIMPLE -> "Subject:$subject\nBody:$body".toByteArray()
    EXTENDED -> Message.Builder().subject(subject).body(body).build().zip()
    TRIVIAL -> (subject + body).toByteArray()
    IGNORE -> ByteArray(0)
}

private fun ackData(type: Plaintext.Type, ackData: ByteArray?): ByteArray? {
    if (ackData != null) {
        return ackData
    } else if (type == MSG) {
        return cryptography().randomBytes(Msg.ACK_LENGTH)
    } else {
        return null
    }
}

/**
 * A plaintext message before encryption or after decryption.
 */
class Plaintext private constructor(
    val type: Type,
    val from: BitmessageAddress,
    to: BitmessageAddress?,
    val encodingCode: Long,
    val message: ByteArray,
    val ackData: ByteArray?,
    ackMessage: Lazy<ObjectMessage?> = lazy { Factory.createAck(from, ackData, ttl) },
    val conversationId: UUID = UUID.randomUUID(),
    var inventoryVector: InventoryVector? = null,
    var signature: ByteArray? = null,
    sent: Long? = null,
    val received: Long? = null,
    var initialHash: ByteArray? = null,
    ttl: Long = TTL.msg,
    val labels: MutableSet<Label> = HashSet(),
    status: Status
) : Streamable {

    var id: Any? = null
        set(id) {
            if (this.id != null) throw IllegalStateException("ID already set")
            field = id
        }

    var to: BitmessageAddress? = to
        set(to) {
            if (to == null) {
                return
            }
            this.to?.let {
                if (it.version != 0L)
                    throw IllegalStateException("Correct address already set")
                if (!Arrays.equals(it.ripe, to.ripe)) {
                    throw IllegalArgumentException("RIPEs don't match")
                }
            }
            field = to
        }

    val stream: Long
        get() = to?.stream ?: from.stream

    val extendedData: ExtendedEncoding? by lazy {
        if (encodingCode == EXTENDED.code) {
            ExtendedEncodingFactory.unzip(message)
        } else {
            null
        }
    }

    val ackMessage: ObjectMessage? by ackMessage

    var status: Status = status
        set(status) {
            if (status != Status.RECEIVED && sent == null && status != Status.DRAFT) {
                sent = UnixTime.now
            }
            field = status
        }

    val encoding: Encoding? by lazy { Encoding.fromCode(encodingCode) }
    var sent: Long? = sent
        private set
    var retries: Int = 0
        private set
    var nextTry: Long? = null
        private set
    val ttl: Long = ttl
        @JvmName("getTTL") get

    constructor(
        type: Type,
        from: BitmessageAddress,
        to: BitmessageAddress?,
        encoding: Encoding,
        message: ByteArray,
        ackData: ByteArray? = null,
        conversationId: UUID = UUID.randomUUID(),
        inventoryVector: InventoryVector? = null,
        signature: ByteArray? = null,
        received: Long? = null,
        initialHash: ByteArray? = null,
        ttl: Long = TTL.msg,
        labels: MutableSet<Label> = HashSet(),
        status: Status
    ) : this(
        type = type,
        from = from,
        to = to,
        encodingCode = encoding.code,
        message = message,
        ackData = ackData(type, ackData),
        conversationId = conversationId,
        inventoryVector = inventoryVector,
        signature = signature,
        received = received,
        initialHash = initialHash,
        ttl = ttl,
        labels = labels,
        status = status
    )

    constructor(
        type: Type,
        from: BitmessageAddress,
        to: BitmessageAddress?,
        encoding: Long,
        message: ByteArray,
        ackMessage: ByteArray?,
        conversationId: UUID = UUID.randomUUID(),
        inventoryVector: InventoryVector? = null,
        signature: ByteArray? = null,
        received: Long? = null,
        initialHash: ByteArray? = null,
        ttl: Long = TTL.msg,
        labels: MutableSet<Label> = HashSet(),
        status: Status
    ) : this(
        type = type,
        from = from,
        to = to,
        encodingCode = encoding,
        message = message,
        ackData = null,
        ackMessage = lazy {
            if (ackMessage != null && ackMessage.isNotEmpty()) {
                Factory.getObjectMessage(
                    3,
                    ByteArrayInputStream(ackMessage),
                    ackMessage.size
                )
            } else null
        },
        conversationId = conversationId,
        inventoryVector = inventoryVector,
        signature = signature,
        received = received,
        initialHash = initialHash,
        ttl = ttl,
        labels = labels,
        status = status
    )

    constructor(
        type: Type,
        from: BitmessageAddress,
        to: BitmessageAddress? = null,
        encoding: Encoding = SIMPLE,
        subject: String,
        body: String,
        ackData: ByteArray? = null,
        conversationId: UUID = UUID.randomUUID(),
        ttl: Long = TTL.msg,
        labels: MutableSet<Label> = HashSet(),
        status: Status = Status.DRAFT
    ) : this(
        type = type,
        from = from,
        to = to,
        encoding = encoding,
        message = message(encoding, subject, body),
        ackData = ackData(type, ackData),
        conversationId = conversationId,
        inventoryVector = null,
        signature = null,
        received = null,
        initialHash = null,
        ttl = ttl,
        labels = labels,
        status = status
    )

    constructor(builder: Builder) : this(
        // Calling prepare() here is somewhat ugly, but also a foolproof way to make sure the builder is properly initialized
        type = builder.prepare().type,
        from = builder.from ?: throw IllegalStateException("sender identity not set"),
        to = builder.to,
        encodingCode = builder.encoding,
        message = builder.message,
        ackData = builder.ackData,
        ackMessage = lazy {
            val ackMsg = builder.ackMessage
            if (ackMsg != null && ackMsg.isNotEmpty()) {
                Factory.getObjectMessage(
                    3,
                    ByteArrayInputStream(ackMsg),
                    ackMsg.size
                )
            } else {
                Factory.createAck(builder.from!!, builder.ackData, builder.ttl)
            }
        },
        conversationId = builder.conversation ?: UUID.randomUUID(),
        inventoryVector = builder.inventoryVector,
        signature = builder.signature,
        sent = builder.sent,
        received = builder.received,
        initialHash = null,
        ttl = builder.ttl,
        labels = LinkedHashSet(builder.labels),
        status = builder.status ?: Status.RECEIVED
    ) {
        id = builder.id
    }

    fun updateNextTry() {
        if (to != null) {
            if (nextTry == null) {
                if (sent != null && to!!.has(Feature.DOES_ACK)) {
                    nextTry = UnixTime.now + ttl
                    retries++
                }
            } else {
                nextTry = nextTry!! + (1 shl retries) * ttl
                retries++
            }
        }
    }

    val subject: String?
        get() {
            val s = Scanner(ByteArrayInputStream(message), "UTF-8")
            val firstLine = s.nextLine()
            return when (encodingCode) {
                EXTENDED.code -> if (Message.TYPE == extendedData?.type) {
                    (extendedData!!.content as? Message)?.subject
                } else {
                    null
                }
                SIMPLE.code -> firstLine.substring("Subject:".length).trim { it <= ' ' }
                else -> {
                    if (firstLine.length > 50) {
                        firstLine.substring(0, 50).trim { it <= ' ' } + "..."
                    } else {
                        firstLine
                    }
                }
            }
        }

    val text: String?
        get() {
            if (encodingCode == EXTENDED.code) {
                return if (Message.TYPE == extendedData?.type) {
                    (extendedData?.content as Message?)?.body
                } else {
                    null
                }
            } else {
                val text = String(message)
                if (encodingCode == SIMPLE.code) {
                    return text.substring(text.indexOf("\nBody:") + 6)
                }
                return text
            }
        }

    fun <T : ExtendedEncoding.ExtendedType> getExtendedData(type: Class<T>): T? {
        val extendedData = extendedData ?: return null
        if (type.isInstance(extendedData.content)) {
            @Suppress("UNCHECKED_CAST")
            return extendedData.content as T
        }
        return null
    }

    val parents: List<InventoryVector>
        get() {
            val extendedData = extendedData ?: return emptyList()
            return if (Message.TYPE == extendedData.type) {
                (extendedData.content as Message).parents
            } else {
                emptyList()
            }
        }

    val files: List<Attachment>
        get() {
            val extendedData = extendedData ?: return emptyList()
            return if (Message.TYPE == extendedData.type) {
                (extendedData.content as Message).files
            } else {
                emptyList()
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Plaintext) return false
        return encoding == other.encoding &&
            from.address == other.from.address &&
            Arrays.equals(message, other.message) &&
            ackMessage == other.ackMessage &&
            Arrays.equals(to?.ripe, other.to?.ripe) &&
            Arrays.equals(signature, other.signature) &&
            status == other.status &&
            sent == other.sent &&
            received == other.received &&
            labels == other.labels
    }

    override fun hashCode(): Int {
        return Objects.hash(from, encoding, message, ackData, to, signature, status, sent, received, labels)
    }

    fun addLabels(vararg labels: Label) {
        Collections.addAll(this.labels, *labels)
    }

    fun addLabels(labels: Collection<Label>?) {
        if (labels != null) {
            this.labels.addAll(labels)
        }
    }

    fun removeLabel(type: Label.Type) {
        labels.removeAll { it.type == type }
    }

    fun isUnread(): Boolean {
        return labels.any { it.type == Label.Type.UNREAD }
    }

    override fun toString(): String {
        val subject = subject
        if (subject?.isNotEmpty() == true) {
            return subject
        } else {
            return Strings.hex(
                initialHash ?: return super.toString()
            )
        }
    }

    enum class Encoding constructor(code: Long) {

        IGNORE(0), TRIVIAL(1), SIMPLE(2), EXTENDED(3);

        var code: Long = 0
            internal set

        init {
            this.code = code
        }

        companion object {

            @JvmStatic
            fun fromCode(code: Long): Encoding? {
                for (e in values()) {
                    if (e.code == code) {
                        return e
                    }
                }
                return null
            }

        }
    }

    enum class Status {

        DRAFT,
        PUBKEY_REQUESTED,
        DOING_PROOF_OF_WORK,
        SENT,
        SENT_ACKNOWLEDGED,
        RECEIVED
    }

    enum class Type {

        MSG, BROADCAST
    }

    fun writer(includeSignature: Boolean): StreamableWriter = Writer(this, includeSignature)

    override fun writer(): StreamableWriter = Writer(this)

    private class Writer(
        private val item: Plaintext,
        private val includeSignature: Boolean = true
    ) : StreamableWriter {

        override fun write(out: OutputStream) {
            Encode.varInt(item.from.version, out)
            Encode.varInt(item.from.stream, out)
            item.from.pubkey?.apply {
                Encode.int32(behaviorBitfield, out)
                out.write(signingKey, 1, 64)
                out.write(encryptionKey, 1, 64)
                if (item.from.version >= 3) {
                    Encode.varInt(nonceTrialsPerByte, out)
                    Encode.varInt(extraBytes, out)
                }
            } ?: {
                Encode.int32(0, out)
                val empty = ByteArray(64)
                out.write(empty)
                out.write(empty)
                if (item.from.version >= 3) {
                    Encode.varInt(0, out)
                    Encode.varInt(0, out)
                }
            }.invoke()
            if (item.type == MSG) {
                // A draft without recipient is allowed, therefore this workaround.
                item.to?.let { out.write(it.ripe) } ?: if (item.status == Status.DRAFT) {
                    out.write(ByteArray(20))
                } else {
                    throw IllegalStateException("No recipient set for message")
                }
            }
            Encode.varInt(item.encodingCode, out)
            Encode.varInt(item.message.size, out)
            out.write(item.message)
            if (item.type == MSG) {
                if (item.to?.has(Feature.DOES_ACK) == true) {
                    val ack = ByteArrayOutputStream()
                    item.ackMessage?.writer()?.write(ack)
                    Encode.varBytes(ack.toByteArray(), out)
                } else {
                    Encode.varInt(0, out)
                }
            }
            if (includeSignature) {
                val sig = item.signature
                if (sig == null) {
                    Encode.varInt(0, out)
                } else {
                    Encode.varBytes(sig, out)
                }
            }
        }

        override fun write(buffer: ByteBuffer) {
            Encode.varInt(item.from.version, buffer)
            Encode.varInt(item.from.stream, buffer)
            if (item.from.pubkey == null) {
                Encode.int32(0, buffer)
                val empty = ByteArray(64)
                buffer.put(empty)
                buffer.put(empty)
                if (item.from.version >= 3) {
                    Encode.varInt(0, buffer)
                    Encode.varInt(0, buffer)
                }
            } else {
                Encode.int32(item.from.pubkey!!.behaviorBitfield, buffer)
                buffer.put(item.from.pubkey!!.signingKey, 1, 64)
                buffer.put(item.from.pubkey!!.encryptionKey, 1, 64)
                if (item.from.version >= 3) {
                    Encode.varInt(item.from.pubkey!!.nonceTrialsPerByte, buffer)
                    Encode.varInt(item.from.pubkey!!.extraBytes, buffer)
                }
            }
            if (item.type == MSG) {
                // A draft without recipient is allowed, therefore this workaround.
                item.to?.let { buffer.put(it.ripe) } ?: if (item.status == Status.DRAFT) {
                    buffer.put(ByteArray(20))
                } else {
                    throw IllegalStateException("No recipient set for message")
                }
            }
            Encode.varInt(item.encodingCode, buffer)
            Encode.varBytes(item.message, buffer)
            if (item.type == MSG) {
                if (item.to!!.has(Feature.DOES_ACK) && item.ackMessage != null) {
                    Encode.varBytes(Encode.bytes(item.ackMessage!!), buffer)
                } else {
                    Encode.varInt(0, buffer)
                }
            }
            if (includeSignature) {
                val sig = item.signature
                if (sig == null) {
                    Encode.varInt(0, buffer)
                } else {
                    Encode.varBytes(sig, buffer)
                }
            }
        }

    }

    class Builder(internal val type: Type) {
        var id: Any? = null
        var inventoryVector: InventoryVector? = null
        var from: BitmessageAddress? = null
        var to: BitmessageAddress? = null
            set(value) {
                if (value != null) {
                    if (type != MSG && to != null)
                        throw IllegalArgumentException("recipient address only allowed for msg")
                    field = value
                }
            }
        var addressVersion: Long = 0
        var stream: Long = 0
        var behaviorBitfield: Int = 0
        var publicSigningKey: ByteArray? = null
        var publicEncryptionKey: ByteArray? = null
        var nonceTrialsPerByte: Long = 0
        var extraBytes: Long = 0
        var destinationRipe: ByteArray? = null
            set(value) {
                if (type != MSG && value != null) throw IllegalArgumentException("ripe only allowed for msg")
                field = value
            }
        var preventAck: Boolean = false
        var encoding: Long = 0
        var message = ByteArray(0)
        var ackData: ByteArray? = null
        var ackMessage: ByteArray? = null
        var signature: ByteArray? = null
        var sent: Long? = null
        var received: Long? = null
        var status: Status? = null
        var labels: Collection<Label> = emptySet()
        var ttl: Long = 0
        var retries: Int = 0
        var nextTry: Long? = null
        var conversation: UUID? = null

        fun id(id: Any): Builder {
            this.id = id
            return this
        }

        fun IV(iv: InventoryVector?): Builder {
            this.inventoryVector = iv
            return this
        }

        fun from(address: BitmessageAddress): Builder {
            from = address
            return this
        }

        fun to(address: BitmessageAddress?): Builder {
            to = address
            return this
        }

        fun addressVersion(addressVersion: Long): Builder {
            this.addressVersion = addressVersion
            return this
        }

        fun stream(stream: Long): Builder {
            this.stream = stream
            return this
        }

        fun behaviorBitfield(behaviorBitfield: Int): Builder {
            this.behaviorBitfield = behaviorBitfield
            return this
        }

        fun publicSigningKey(publicSigningKey: ByteArray): Builder {
            this.publicSigningKey = publicSigningKey
            return this
        }

        fun publicEncryptionKey(publicEncryptionKey: ByteArray): Builder {
            this.publicEncryptionKey = publicEncryptionKey
            return this
        }

        fun nonceTrialsPerByte(nonceTrialsPerByte: Long): Builder {
            this.nonceTrialsPerByte = nonceTrialsPerByte
            return this
        }

        fun extraBytes(extraBytes: Long): Builder {
            this.extraBytes = extraBytes
            return this
        }

        fun destinationRipe(ripe: ByteArray?): Builder {
            this.destinationRipe = ripe
            return this
        }

        @JvmOverloads
        fun preventAck(preventAck: Boolean = true): Builder {
            this.preventAck = preventAck
            return this
        }

        fun encoding(encoding: Encoding): Builder {
            this.encoding = encoding.code
            return this
        }

        fun encoding(encoding: Long): Builder {
            this.encoding = encoding
            return this
        }

        fun message(message: ExtendedEncoding): Builder {
            this.encoding = EXTENDED.code
            this.message = message.zip()
            return this
        }

        fun message(subject: String, message: String): Builder {
            try {
                this.encoding = SIMPLE.code
                this.message = "Subject:$subject\nBody:$message".toByteArray(charset("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                throw ApplicationException(e)
            }
            return this
        }

        fun message(message: ByteArray): Builder {
            this.message = message
            return this
        }

        fun ackMessage(ack: ByteArray?): Builder {
            if (type != MSG && ack != null) throw IllegalArgumentException("ackMessage only allowed for msg")
            this.ackMessage = ack
            return this
        }

        fun ackData(ackData: ByteArray?): Builder {
            if (type != MSG && ackData != null)
                throw IllegalArgumentException("ackMessage only allowed for msg")
            this.ackData = ackData
            return this
        }

        fun signature(signature: ByteArray?): Builder {
            this.signature = signature
            return this
        }

        fun sent(sent: Long?): Builder {
            this.sent = sent
            return this
        }

        fun received(received: Long?): Builder {
            this.received = received
            return this
        }

        fun status(status: Status): Builder {
            this.status = status
            return this
        }

        fun labels(labels: Collection<Label>): Builder {
            this.labels = labels
            return this
        }

        fun ttl(ttl: Long): Builder {
            this.ttl = ttl
            return this
        }

        fun retries(retries: Int): Builder {
            this.retries = retries
            return this
        }

        fun nextTry(nextTry: Long?): Builder {
            this.nextTry = nextTry
            return this
        }

        fun conversation(id: UUID): Builder {
            this.conversation = id
            return this
        }

        internal fun prepare(): Builder {
            if (from == null) {
                from = BitmessageAddress(
                    Factory.createPubkey(
                        addressVersion,
                        stream,
                        publicSigningKey!!,
                        publicEncryptionKey!!,
                        nonceTrialsPerByte,
                        extraBytes,
                        behaviorBitfield
                    )
                )
            }
            if (to == null && type != Type.BROADCAST && destinationRipe != null) {
                to = BitmessageAddress(0, 0, destinationRipe!!)
            }
            if (preventAck) {
                ackData = null
                ackMessage = null
            } else if (type == MSG && ackMessage == null && ackData == null && to?.has(Feature.DOES_ACK) == true) {
                ackData = cryptography().randomBytes(Msg.ACK_LENGTH)
            }
            if (ttl <= 0) {
                ttl = TTL.msg
            }
            return this
        }

        @JvmSynthetic
        inline fun build(block: Builder.() -> Unit): Plaintext {
            block(this)
            return build()
        }

        fun build(): Plaintext {
            return Plaintext(this)
        }
    }

    companion object {

        @JvmStatic
        fun read(type: Type, input: InputStream): Plaintext {
            return readWithoutSignature(type, input)
                .signature(Decode.varBytes(input))
                .received(UnixTime.now)
                .build()
        }

        @JvmStatic
        fun readWithoutSignature(type: Type, input: InputStream): Plaintext.Builder {
            val version = Decode.varInt(input)
            return Builder(type)
                .addressVersion(version)
                .stream(Decode.varInt(input))
                .behaviorBitfield(Decode.int32(input))
                .publicSigningKey(Decode.bytes(input, 64))
                .publicEncryptionKey(Decode.bytes(input, 64))
                .nonceTrialsPerByte(if (version >= 3) Decode.varInt(input) else 0)
                .extraBytes(if (version >= 3) Decode.varInt(input) else 0)
                .destinationRipe(if (type == MSG) Decode.bytes(input, 20).let {
                    if (it.any { x -> x != 0.toByte() }) it else null
                } else null)
                .encoding(Decode.varInt(input))
                .message(Decode.varBytes(input))
                .ackMessage(if (type == MSG) Decode.varBytes(input) else null)
        }

        @JvmSynthetic
        inline fun build(type: Type, block: Builder.() -> Unit): Plaintext {
            val builder = Builder(type)
            block(builder)
            return builder.build()
        }

    }
}

data class Conversation(val id: UUID, val subject: String, val messages: List<Plaintext>) {
    val participants = messages
        .map { it.from }
        .filter { it.privateKey == null || it.isChan }
        .distinct()

    val extract: String by lazy { messages.lastOrNull()?.text ?: "" }

    fun hasUnread() = messages.any { it.isUnread() }
}
