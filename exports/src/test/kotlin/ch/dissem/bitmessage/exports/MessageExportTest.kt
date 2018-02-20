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
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.utils.ConversationServiceTest
import ch.dissem.bitmessage.utils.Singleton
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageExportTest {
    private val inbox = Label("Inbox", Label.Type.INBOX, 0x0000ff)
    private val outbox = Label("Outbox", Label.Type.OUTBOX, 0x00ff00)
    private val unread = Label("Unread", Label.Type.UNREAD, 0x000000)
    private val trash = Label("Trash", Label.Type.TRASH, 0x555555)

    private val labels = listOf(
        inbox,
        outbox,
        unread,
        trash
    )
    private val labelMap = MessageExport.createLabelMap(labels)

    init {
        TestUtils.mockedInternalContext(cryptography = BouncyCryptography())
    }

    @Test
    fun `ensure labels are exported`() {
        val export = MessageExport.exportLabels(labels)
        print(export.toJsonString(true))
        assertEquals(labels, MessageExport.importLabels(export))
    }

    @Test
    fun `ensure messages are exported`() {
        val messages = listOf(
            Plaintext.Builder(Plaintext.Type.MSG)
                .ackData(Singleton.cryptography().randomBytes(32))
                .from(BitmessageAddress("BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci"))
                .to(BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn"))
                .message("Subject", "Message")
                .status(Plaintext.Status.RECEIVED)
                .labels(listOf(inbox))
                .build(),
            Plaintext.Builder(Plaintext.Type.BROADCAST)
                .from(BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
                .message("Subject", "Message")
                .labels(listOf(inbox, unread))
                .status(Plaintext.Status.SENT)
                .build(),
            Plaintext.Builder(Plaintext.Type.MSG)
                .ttl(1)
                .message("subject", "message")
                .from(TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"))
                .to(TestUtils.loadContact())
                .labels(listOf(trash))
                .status(Plaintext.Status.SENT_ACKNOWLEDGED)
                .build(),
            *ConversationServiceTest.conversation(
                TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8"),
                TestUtils.loadContact()
            ).toTypedArray()
        )
        val export = MessageExport.exportMessages(messages)
        print(export.toJsonString(true))
        assertEquals(messages, MessageExport.importMessages(export, labelMap))
    }
}
