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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.entity.Plaintext

/**
 * Defines and sets labels. Note that it should also update the status field of a message.
 * Generally it's highly advised to override the [DefaultLabeler] whenever possible,
 * instead of directly implementing the interface.
 *
 * As the labeler gets called whenever the state of a message changes, it can also be used
 * as a listener.
 */
interface Labeler {
    /**
     * Sets the labels of a newly received message.
     *
     * @param msg an unlabeled message or broadcast
     */
    fun setLabels(msg: Plaintext)

    fun markAsDraft(msg: Plaintext)

    /**
     * It is paramount that this methods marks the [Plaintext] object with status
     * [Plaintext.Status.PUBKEY_REQUESTED] (see [DefaultLabeler])
     */
    fun markAsSending(msg: Plaintext)

    fun markAsSent(msg: Plaintext)

    fun markAsAcknowledged(msg: Plaintext)

    fun markAsRead(msg: Plaintext)

    fun markAsUnread(msg: Plaintext)

    fun delete(msg: Plaintext)

    fun archive(msg: Plaintext)
}