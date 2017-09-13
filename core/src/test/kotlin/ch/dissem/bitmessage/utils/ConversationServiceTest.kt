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

package ch.dissem.bitmessage.utils

import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.ports.MessageRepository
import ch.dissem.bitmessage.utils.TestUtils.RANDOM
import com.nhaarman.mockito_kotlin.*
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import java.util.*

class ConversationServiceTest : TestBase() {
    private val alice = BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8")
    private val bob = BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj")

    private val messageRepository = mock<MessageRepository>()
    private val conversationService = spy(ConversationService(messageRepository))

    private val conversation = conversation(alice, bob)

    @Test
    fun `ensure conversation is sorted properly`() {
        MockitoKotlin.registerInstanceCreator { UUID.randomUUID() }
        val expected = conversation

        doReturn(expected).whenever(conversationService).getConversation(any<UUID>())
        val actual = conversationService.getConversation(UUID.randomUUID())
        assertThat(actual, `is`(expected))
    }

    companion object {
        private var timer = 2
        fun conversation(alice: BitmessageAddress, bob: BitmessageAddress): List<Plaintext> {
            val result = LinkedList<Plaintext>()

            val older = plaintext(alice, bob,
                Message.Builder()
                    .subject("hey there")
                    .body("does it work?")
                    .build(),
                Plaintext.Status.SENT)
            result.add(older)

            val root = plaintext(alice, bob,
                Message.Builder()
                    .subject("new test")
                    .body("There's a new test in town!")
                    .build(),
                Plaintext.Status.SENT)
            result.add(root)

            result.add(
                plaintext(bob, alice,
                    Message.Builder()
                        .subject("Re: new test (1a)")
                        .body("Nice!")
                        .addParent(root)
                        .build(),
                    Plaintext.Status.RECEIVED)
            )

            val latest = plaintext(bob, alice,
                Message.Builder()
                    .subject("Re: new test (2b)")
                    .body("PS: it did work!")
                    .addParent(root)
                    .addParent(older)
                    .build(),
                Plaintext.Status.RECEIVED)
            result.add(latest)

            result.add(
                plaintext(alice, bob,
                    Message.Builder()
                        .subject("Re: new test (2)")
                        .body("")
                        .addParent(latest)
                        .build(),
                    Plaintext.Status.DRAFT)
            )

            return result
        }

        fun plaintext(from: BitmessageAddress, to: BitmessageAddress,
                      content: ExtendedEncoding, status: Plaintext.Status): Plaintext {
            val builder = Plaintext.Builder(MSG)
                .IV(TestUtils.randomInventoryVector())
                .from(from)
                .to(to)
                .message(content)
                .status(status)
            if (status !== Plaintext.Status.DRAFT && status !== Plaintext.Status.DOING_PROOF_OF_WORK) {
                builder.received(5L * ++timer - RANDOM.nextInt(10))
            }
            return builder.build()
        }
    }
}
