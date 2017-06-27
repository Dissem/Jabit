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
import ch.dissem.bitmessage.entity.Encrypted
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST
import ch.dissem.bitmessage.entity.PlaintextHolder
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.utils.Singleton.cryptography
import java.util.*

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 * Broadcasts are version 4 or 5.
 */
abstract class Broadcast protected constructor(version: Long, override val stream: Long, protected var encrypted: CryptoBox?, override var plaintext: Plaintext?) : ObjectPayload(version), Encrypted, PlaintextHolder {

    override val isSigned: Boolean = true

    override var signature: ByteArray?
        get() = plaintext?.signature
        set(signature) {
            plaintext?.signature = signature ?: throw IllegalStateException("no plaintext data available")
        }

    override fun encrypt(publicKey: ByteArray) {
        this.encrypted = CryptoBox(plaintext ?: throw IllegalStateException("no plaintext data available"), publicKey)
    }

    fun encrypt() {
        encrypt(cryptography().createPublicKey(plaintext?.from?.publicDecryptionKey ?: return))
    }

    @Throws(DecryptionFailedException::class)
    override fun decrypt(privateKey: ByteArray) {
        plaintext = Plaintext.read(BROADCAST, encrypted?.decrypt(privateKey) ?: return)
    }

    @Throws(DecryptionFailedException::class)
    fun decrypt(address: BitmessageAddress) {
        decrypt(address.publicDecryptionKey)
    }

    override val isDecrypted: Boolean
        get() = plaintext != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Broadcast) return false
        return stream == other.stream && (encrypted == other.encrypted || plaintext == other.plaintext)
    }

    override fun hashCode(): Int {
        return Objects.hash(stream)
    }

    companion object {
        fun getVersion(address: BitmessageAddress): Long {
            return if (address.version < 4) 4L else 5L
        }
    }
}
