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

/*
 * Copyright 2015 Christian Basler
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

package ch.dissem.bitmessage.wif

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.valueobject.PrivateKey.Companion.PRIVATE_KEY_SIZE
import ch.dissem.bitmessage.utils.Base58
import ch.dissem.bitmessage.utils.Singleton.cryptography
import org.ini4j.Ini
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.StringWriter

/**
 * @author Christian Basler
 */
class WifExporter(private val ctx: BitmessageContext) {
    private val ini = Ini()

    fun addAll(): WifExporter {
        ctx.addresses.getIdentities().forEach { addIdentity(it) }
        return this
    }

    fun addAll(identities: Collection<BitmessageAddress>): WifExporter {
        identities.forEach { addIdentity(it) }
        return this
    }

    fun addIdentity(identity: BitmessageAddress): WifExporter {
        val section = ini.add(identity.address)
        section.add("label", identity.alias)
        section.add("enabled", true)
        section.add("decoy", false)
        if (identity.isChan) {
            section.add("chan", identity.isChan)
        }
        section.add("noncetrialsperbyte", identity.pubkey!!.nonceTrialsPerByte)
        section.add("payloadlengthextrabytes", identity.pubkey!!.extraBytes)
        section.add("privsigningkey", exportSecret(identity.privateKey!!.privateSigningKey))
        section.add("privencryptionkey", exportSecret(identity.privateKey!!.privateEncryptionKey))
        return this
    }

    private fun exportSecret(privateKey: ByteArray): String {
        if (privateKey.size != PRIVATE_KEY_SIZE) {
            throw IllegalArgumentException("Private key of length 32 expected, but was " + privateKey.size)
        }
        val result = ByteArray(37)
        result[0] = 0x80.toByte()
        System.arraycopy(privateKey, 0, result, 1, PRIVATE_KEY_SIZE)
        val hash = cryptography().doubleSha256(result, PRIVATE_KEY_SIZE + 1)
        System.arraycopy(hash, 0, result, PRIVATE_KEY_SIZE + 1, 4)
        return Base58.encode(result)
    }

    fun write(file: File) {
        file.createNewFile()
        FileOutputStream(file).use { out -> write(out) }
    }

    fun write(out: OutputStream) {
        ini.store(out)
    }

    override fun toString(): String {
        val writer = StringWriter()
        ini.store(writer)
        return writer.toString()
    }
}
