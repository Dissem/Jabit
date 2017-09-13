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

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.utils.TestUtils
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Test

class ContactExportTest {

    init {
        TestUtils.mockedInternalContext(cryptography = BouncyCryptography())
    }

    @Test
    fun `ensure contacts are exported`() {
        val alice = BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj")
        alice.alias = "Alice"
        alice.isSubscribed = true
        val contacts = listOf(
            BitmessageAddress("BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci"),
            TestUtils.loadContact(),
            alice
        )
        val export = ContactExport.exportContacts(contacts)
        print(export.toJsonString(true))
        assertThat(ContactExport.importContacts(export), `is`(contacts))
    }

    @Test
    fun `ensure private keys are omitted by default`() {
        val contacts = listOf(
            BitmessageAddress.chan(1, "test")
        )
        val export = ContactExport.exportContacts(contacts)
        print(export.toJsonString(true))
        val import = ContactExport.importContacts(export)
        assertThat(import.size, `is`(1))
        assertThat(import[0].isChan, `is`(true))
        assertThat(import[0].privateKey, `is`(nullValue()))
    }

    @Test
    fun `ensure private keys are exported if flag is set`() {
        val contacts = listOf(
            BitmessageAddress.chan(1, "test")
        )
        val export = ContactExport.exportContacts(contacts, true)
        print(export.toJsonString(true))
        val import = ContactExport.importContacts(export)
        assertThat(import.size, `is`(1))
        assertThat(import[0].isChan, `is`(true))
        assertThat(import[0].privateKey, `is`(contacts[0].privateKey))
    }
}
