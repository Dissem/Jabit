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
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.utils.Base58
import ch.dissem.bitmessage.utils.Singleton.cryptography
import org.ini4j.Ini
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

/**
 * @author Christian Basler
 */
class WifImporter constructor(
    private val ctx: BitmessageContext,
    `in`: InputStream,
    vararg features: Pubkey.Feature
) {
    private val identities = LinkedList<BitmessageAddress>()

    constructor(ctx: BitmessageContext, file: File) : this(ctx, FileInputStream(file))

    constructor(ctx: BitmessageContext, data: String) : this(ctx, ByteArrayInputStream(data.toByteArray(charset("utf-8"))))

    init {
        val ini = Ini()
        ini.load(`in`)

        for ((key, section) in ini) {
            if (!key.startsWith("BM-"))
                continue

            val address = Factory.createIdentityFromPrivateKey(
                key,
                getSecret(section["privsigningkey"] ?: throw ApplicationException("privsigningkey missing for $key")),
                getSecret(section["privencryptionkey"] ?: throw ApplicationException("privencryptionkey missing for $key")),
                section["noncetrialsperbyte"]?.toLongOrNull() ?: throw ApplicationException("noncetrialsperbyte missing for $key"),
                section["payloadlengthextrabytes"]?.toLongOrNull() ?: throw ApplicationException("payloadlengthextrabytes missing for $key"),
                Pubkey.Feature.bitfield(*features)
            )
            if (section.containsKey("chan")) {
                address.isChan = java.lang.Boolean.parseBoolean(section["chan"])
            }
            address.alias = section["label"]
            identities.add(address)
        }
    }

    private fun getSecret(walletImportFormat: String): ByteArray {
        val bytes = Base58.decode(walletImportFormat)
        if (bytes[0] != WIF_FIRST_BYTE)
            throw ApplicationException("Unknown format: 0x80 expected as first byte, but secret " + walletImportFormat +
                " was " + bytes[0])
        if (bytes.size != WIF_SECRET_LENGTH)
            throw ApplicationException("Unknown format: " + WIF_SECRET_LENGTH +
                " bytes expected, but secret " + walletImportFormat + " was " + bytes.size + " long")

        val hash = cryptography().doubleSha256(bytes, 33)
        (0..3)
            .filter { hash[it] != bytes[33 + it] }
            .forEach { throw ApplicationException("Hash check failed for secret " + walletImportFormat) }
        return Arrays.copyOfRange(bytes, 1, 33)
    }

    fun getIdentities(): List<BitmessageAddress> {
        return identities
    }

    fun importAll(): WifImporter {
        identities.forEach { ctx.addresses.save(it) }
        return this
    }

    fun importAll(identities: Collection<BitmessageAddress>): WifImporter {
        identities.forEach { ctx.addresses.save(it) }
        return this
    }

    fun importIdentity(identity: BitmessageAddress): WifImporter {
        ctx.addresses.save(identity)
        return this
    }

    companion object {
        private const val WIF_FIRST_BYTE = 0x80.toByte()
        private const val WIF_SECRET_LENGTH = 37
    }
}
