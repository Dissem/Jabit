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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.entity.valueobject.InventoryVector;

/**
 * Callback for message sending events, mostly so the user can be notified when POW is done.
 */
public interface MessageCallback {
    /**
     * Called before calculation of proof of work begins.
     */
    void proofOfWorkStarted(ObjectPayload message);

    /**
     * Called after calculation of proof of work finished.
     */
    void proofOfWorkCompleted(ObjectPayload message);

    /**
     * Called once the message is offered to the network. Please note that this doesn't mean the message was sent,
     * if the client is not connected to the network it's just stored in the inventory.
     * <p>
     * Also, please note that this is where the original payload as well as the {@link InventoryVector} of the sent
     * message is available. If the callback needs the IV for some reason, it should be retrieved here. (Plaintext
     * and Broadcast messages will have their IV property set automatically though.)
     * </p>
     */
    void messageOffered(ObjectPayload message, InventoryVector iv);

    /**
     * This isn't called yet, as ACK messages aren't being processed yet. Also, this is only relevant for Plaintext
     * messages.
     */
    void messageAcknowledged(InventoryVector iv);
}
