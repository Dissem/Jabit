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

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 * Broadcasts are version 4 or 5.
 */
public class V4Broadcast extends Broadcast {
    protected V4Broadcast(long version, long stream, CryptoBox encrypted, Plaintext plaintext) {
        super(version, stream, encrypted, plaintext);
    }

    public V4Broadcast(BitmessageAddress senderAddress, Plaintext plaintext) {
        super(4, senderAddress.getStream(), null, plaintext);
        if (senderAddress.getVersion() >= 4)
            throw new IllegalArgumentException("Address version 3 or older expected, but was " + senderAddress.getVersion());
    }

    public static V4Broadcast read(InputStream in, long stream, int length) throws IOException {
        return new V4Broadcast(4, stream, CryptoBox.read(in, length), null);
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BROADCAST;
    }

    @Override
    public void writeBytesToSign(OutputStream out) throws IOException {
        plaintext.write(out, false);
    }

    @Override
    public void write(OutputStream out) throws IOException {
        encrypted.write(out);
    }
}
