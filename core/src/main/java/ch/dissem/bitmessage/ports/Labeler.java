/*
 * Copyright 2016 Christian Basler
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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.Plaintext;

/**
 * Defines and sets labels. Note that it should also update the status field of a message.
 * Generally it's highly advised to override the {@link DefaultLabeler} whenever possible,
 * instead of directly implementing the interface.
 * <p>
 * As the labeler gets called whenever the state of a message changes, it can also be used
 * as a listener.
 * </p>
 */
public interface Labeler {
    /**
     * Sets the labels of a newly received message.
     *
     * @param msg an unlabeled message or broadcast
     */
    void setLabels(Plaintext msg);

    void markAsDraft(Plaintext msg);

    /**
     * It is paramount that this methods marks the {@link Plaintext} object with status
     * {@link Plaintext.Status#PUBKEY_REQUESTED} (see {@link DefaultLabeler})
     */
    void markAsSending(Plaintext msg);

    void markAsSent(Plaintext msg);

    void markAsAcknowledged(Plaintext msg);

    void markAsRead(Plaintext msg);

    void markAsUnread(Plaintext msg);

    void delete(Plaintext msg);

    void archive(Plaintext msg);
}
