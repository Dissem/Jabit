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
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.ports.AddressRepository
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WifExporterTest {
    private val repo = mock<AddressRepository>()
    private lateinit var ctx: BitmessageContext
    private lateinit var importer: WifImporter
    private lateinit var exporter: WifExporter

    @BeforeEach
    fun setUp() {
        ctx = BitmessageContext.build {
            cryptography = BouncyCryptography()
            networkHandler = mock()
            inventory = mock()
            labelRepo = mock()
            messageRepo = mock()
            proofOfWorkRepo = mock()
            nodeRegistry = mock()
            addressRepo = repo
            listener {}
        }
        importer = WifImporter(ctx, javaClass.classLoader.getResourceAsStream("nuked.dat"))
        assumeTrue(importer.getIdentities().size == 81)
        exporter = WifExporter(ctx)
    }

    @Test
    fun `ensure all identities in context are added`() {
        whenever(repo.getIdentities()).thenReturn(importer.getIdentities())
        exporter.addAll()
        val result = exporter.toString()
        val count = result.count { it == '[' }
        assertEquals(importer.getIdentities().size, count)
    }

    @Test
    fun `ensure all from a collection are added`() {
        exporter.addAll(importer.getIdentities())
        val result = exporter.toString()
        val count = result.count { it == '[' }
        assertEquals(importer.getIdentities().size, count)
    }

    @Test
    fun `ensure identity is added`() {
        val expected = "[BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn]" + System.lineSeparator() +
            "label = Nuked Address" + System.lineSeparator() +
            "enabled = true" + System.lineSeparator() +
            "decoy = false" + System.lineSeparator() +
            "noncetrialsperbyte = 320" + System.lineSeparator() +
            "payloadlengthextrabytes = 14000" + System.lineSeparator() +
            "privsigningkey = 5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9" + System.lineSeparator() +
            "privencryptionkey = 5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck" + System.lineSeparator() +
            System.lineSeparator()
        importer = WifImporter(ctx, expected)
        exporter.addIdentity(importer.getIdentities()[0])
        assertEquals(expected, exporter.toString())
    }

    @Test
    fun `ensure chan is added`() {
        val expected = "[BM-2cW67GEKkHGonXKZLCzouLLxnLym3azS8r]" + System.lineSeparator() +
            "label = general" + System.lineSeparator() +
            "enabled = true" + System.lineSeparator() +
            "decoy = false" + System.lineSeparator() +
            "chan = true" + System.lineSeparator() +
            "noncetrialsperbyte = 1000" + System.lineSeparator() +
            "payloadlengthextrabytes = 1000" + System.lineSeparator() +
            "privsigningkey = 5Jnbdwc4u4DG9ipJxYLznXSvemkRFueQJNHujAQamtDDoX3N1eQ" + System.lineSeparator() +
            "privencryptionkey = 5JrDcFtQDv5ydcHRW6dfGUEvThoxCCLNEUaxQfy8LXXgTJzVAcq" + System.lineSeparator() +
            System.lineSeparator()
        val chan = ctx.joinChan("general", "BM-2cW67GEKkHGonXKZLCzouLLxnLym3azS8r")
        exporter.addIdentity(chan)
        assertEquals(expected, exporter.toString())
    }
}
