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

package ch.dissem.bitmessage.utils;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding;
import ch.dissem.bitmessage.entity.valueobject.extended.Message;
import ch.dissem.bitmessage.ports.MessageRepository;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.TestUtils.RANDOM;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConversationServiceTest {
    private BitmessageAddress alice = new BitmessageAddress("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
    private BitmessageAddress bob = new BitmessageAddress("BM-2cTtkBnb4BUYDndTKun6D9PjtueP2h1bQj");

    private MessageRepository messageRepository = mock(MessageRepository.class);
    private ConversationService conversationService = new ConversationService(messageRepository);

    static {
        Singleton.initialize(new BouncyCryptography());
    }

    @Test
    public void ensureConversationIsSortedProperly() {
        List<Plaintext> expected = getConversation();

        when(conversationService.getConversation(any(UUID.class))).thenReturn(expected);
        List<Plaintext> actual = conversationService.getConversation(UUID.randomUUID());
        assertThat(actual, is(expected));
    }

    private List<Plaintext> getConversation() {
        List<Plaintext> result = new LinkedList<>();

        Plaintext older = plaintext(alice, bob,
            new Message.Builder()
                .subject("hey there")
                .body("does it work?")
                .build(),
            Plaintext.Status.SENT);
        result.add(older);

        Plaintext root = plaintext(alice, bob,
            new Message.Builder()
                .subject("new test")
                .body("There's a new test in town!")
                .build(),
            Plaintext.Status.SENT);
        result.add(root);

        result.add(
            plaintext(bob, alice,
                new Message.Builder()
                    .subject("Re: new test (1a)")
                    .body("Nice!")
                    .addParent(root)
                    .build(),
                Plaintext.Status.RECEIVED)
        );

        Plaintext latest = plaintext(bob, alice,
            new Message.Builder()
                .subject("Re: new test (2b)")
                .body("PS: it did work!")
                .addParent(root)
                .addParent(older)
                .build(),
            Plaintext.Status.RECEIVED);
        result.add(latest);

        result.add(
            plaintext(alice, bob,
                new Message.Builder()
                    .subject("Re: new test (2)")
                    .body("")
                    .addParent(latest)
                    .build(),
                Plaintext.Status.DRAFT)
        );

        return result;
    }

    private int timer = 2;

    private Plaintext plaintext(BitmessageAddress from, BitmessageAddress to,
                                ExtendedEncoding content, Plaintext.Status status) {
        Plaintext.Builder builder = new Plaintext.Builder(MSG)
            .IV(TestUtils.randomInventoryVector())
            .from(from)
            .to(to)
            .message(content)
            .status(status);
        if (status != Plaintext.Status.DRAFT && status != Plaintext.Status.DOING_PROOF_OF_WORK) {
            builder.received(5L * ++timer - RANDOM.nextInt(10));
        }
        return builder.build();
    }
}
