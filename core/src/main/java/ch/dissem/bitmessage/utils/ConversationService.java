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

import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;
import ch.dissem.bitmessage.ports.MessageRepository;

import java.util.*;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Service that helps with conversations.
 */
public class ConversationService {
    private final MessageRepository messageRepository;

    private final Pattern SUBJECT_PREFIX = Pattern.compile("^(re|fwd?):", CASE_INSENSITIVE);

    public ConversationService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Retrieve the whole conversation from one single message. If the message isn't part
     * of a conversation, a singleton list containing the given message is returned. Otherwise
     * it's the same as {@link #getConversation(UUID)}
     *
     * @param message
     * @return a list of messages that belong to the same conversation.
     */
    public List<Plaintext> getConversation(Plaintext message) {
        if (message.getConversationId() == null) {
            return Collections.singletonList(message);
        }
        return getConversation(message.getConversationId());
    }

    private LinkedList<Plaintext> sorted(Collection<Plaintext> collection) {
        LinkedList<Plaintext> result = new LinkedList<>(collection);
        Collections.sort(result, new Comparator<Plaintext>() {
            @Override
            public int compare(Plaintext o1, Plaintext o2) {
                //noinspection NumberEquality - if both are null (if both are the same, it's a bonus)
                if (o1.getReceived() == o2.getReceived()) {
                    return 0;
                }
                if (o1.getReceived() == null) {
                    return -1;
                }
                if (o2.getReceived() == null) {
                    return 1;
                }
                return -o1.getReceived().compareTo(o2.getReceived());
            }
        });
        return result;
    }

    public List<Plaintext> getConversation(UUID conversationId) {
        LinkedList<Plaintext> messages = sorted(messageRepository.getConversation(conversationId));
        Map<InventoryVector, Plaintext> map = new HashMap<>(messages.size());
        for (Plaintext message : messages) {
            if (message.getInventoryVector() != null) {
                map.put(message.getInventoryVector(), message);
            }
        }

        LinkedList<Plaintext> result = new LinkedList<>();
        while (!messages.isEmpty()) {
            Plaintext last = messages.poll();
            int pos = lastParentPosition(last, result);
            result.add(pos, last);
            addAncestors(last, result, messages, map);
        }
        return result;
    }

    public String getSubject(List<Plaintext> conversation) {
        if (conversation.isEmpty()) {
            return null;
        }
        // TODO: this has room for improvement
        String subject = conversation.get(0).getSubject();
        Matcher matcher = SUBJECT_PREFIX.matcher(subject);
        if (matcher.find()) {
            return subject.substring(matcher.end()).trim();
        }
        return subject.trim();
    }

    private int lastParentPosition(Plaintext child, LinkedList<Plaintext> messages) {
        Iterator<Plaintext> plaintextIterator = messages.descendingIterator();
        int i = 0;
        while (plaintextIterator.hasNext()) {
            Plaintext next = plaintextIterator.next();
            if (isParent(next, child)) {
                break;
            }
            i++;
        }
        return messages.size() - i;
    }

    private boolean isParent(Plaintext item, Plaintext child) {
        for (InventoryVector parentId : child.getParents()) {
            if (parentId.equals(item.getInventoryVector())) {
                return true;
            }
        }
        return false;
    }

    private void addAncestors(Plaintext message, LinkedList<Plaintext> result, LinkedList<Plaintext> messages, Map<InventoryVector, Plaintext> map) {
        for (InventoryVector parentKey : message.getParents()) {
            Plaintext parent = map.remove(parentKey);
            if (parent != null) {
                messages.remove(parent);
                result.addFirst(parent);
                addAncestors(parent, result, messages, map);
            }
        }
    }
}
