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
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WifImporterTest {
    private val repo = mock<AddressRepository>()
    private lateinit var ctx: BitmessageContext
    private lateinit var importer: WifImporter

    @Before
    fun setUp() {
        ctx = BitmessageContext.build {
            cryptography = BouncyCryptography()
            networkHandler = mock()
            inventory = mock()
            messageRepo = mock()
            proofOfWorkRepo = mock()
            nodeRegistry = mock()
            addressRepo = repo
            listener { }
        }
        importer = WifImporter(ctx, javaClass.classLoader.getResourceAsStream("nuked.dat"))
    }


    @Test
    fun `ensure single identity is imported`() {
        importer = WifImporter(ctx, "[BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci]\n" +
            "label = Nuked Address\n" +
            "enabled = true\n" +
            "decoy = false\n" +
            "noncetrialsperbyte = 320\n" +
            "payloadlengthextrabytes = 14000\n" +
            "privsigningkey = 5JU5t2JA58sP5aJwKAcrYg5EpBA9bJPrBSaFfaZ7ogmwTMDCfHL\n" +
            "privencryptionkey = 5Kkx5MwjQcM4kyduKvCEPM6nVNynMdRcg88VQ5iVDWUekMz1igH")
        assertEquals(1, importer.getIdentities().size)
        val identity = importer.getIdentities()[0]
        assertEquals("BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci", identity.address)
        assertEquals("Nuked Address", identity.alias)
        assertEquals(320L, identity.pubkey?.nonceTrialsPerByte)
        assertEquals(14000L, identity.pubkey?.extraBytes)
        assertNotNull("Private key", identity.privateKey)
        assertEquals(32, identity.privateKey?.privateEncryptionKey?.size)
        assertEquals(32, identity.privateKey?.privateSigningKey?.size)
        assertFalse(identity.isChan)
    }

    @Test
    fun `ensure all identities are retrieved`() {
        val identities = importer.getIdentities()
        assertEquals(81, identities.size)
    }

    @Test
    fun `ensure all identities are imported`() {
        importer.importAll()
        verify(repo, times(81)).save(any())
    }

    @Test
    fun `ensure all identities in collection are imported`() {
        val identities = importer.getIdentities()
        importer.importAll(identities)
        for (identity in identities) {
            verify(repo, times(1)).save(identity)
        }
    }

    @Test
    fun `ensure single identity from list is imported`() {
        val identities = importer.getIdentities()
        importer.importIdentity(identities[0])
        verify(repo, times(1)).save(identities[0])
    }

    @Test
    fun `ensure chan is imported`() {
        importer = WifImporter(ctx, "[BM-2cW67GEKkHGonXKZLCzouLLxnLym3azS8r]\n" +
            "label = [chan] general\n" +
            "enabled = true\n" +
            "decoy = false\n" +
            "chan = true\n" +
            "noncetrialsperbyte = 1000\n" +
            "payloadlengthextrabytes = 1000\n" +
            "privsigningkey = 5Jnbdwc4u4DG9ipJxYLznXSvemkRFueQJNHujAQamtDDoX3N1eQ\n" +
            "privencryptionkey = 5JrDcFtQDv5ydcHRW6dfGUEvThoxCCLNEUaxQfy8LXXgTJzVAcq\n")
        assertEquals(1, importer.getIdentities().size)
        val chan = importer.getIdentities()[0]
        assertTrue(chan.isChan)
    }
}
