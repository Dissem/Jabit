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

package ch.dissem.bitmessage.entity.payload;

/**
 * Public keys for signing and encryption, the answer to a 'getpubkey' request.
 */
public interface Pubkey extends ObjectPayload {
    // bits 0 through 29 are yet undefined
    /**
     * Receiving node expects that the RIPE hash encoded in their address preceedes the encrypted message data of msg
     * messages bound for them.
     */
    int FEATURE_INCLUDE_DESTINATION = 30;
    /**
     * If true, the receiving node does send acknowledgements (rather than dropping them).
     */
    int FEATURE_DOES_ACK = 31;

    long getVersion();

    byte[] getSigningKey();

    byte[] getEncryptionKey();
}