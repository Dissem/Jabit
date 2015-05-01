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

import ch.dissem.bitmessage.utils.Decode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 * Broadcasts are version 4 or 5.
 */
public class V4Broadcast implements Broadcast {
    private long stream;
    private byte[] encrypted;
    private UnencryptedMessage unencrypted;

    protected V4Broadcast(long stream, byte[] encrypted) {
        this.stream = stream;
        this.encrypted = encrypted;
    }

    public static V4Broadcast read(InputStream is, long stream, int length) throws IOException {
        return new V4Broadcast(stream, Decode.bytes(is, length));
    }

    @Override
    public ObjectType getType() {
        return ObjectType.BROADCAST;
    }

    @Override
    public long getStream() {
        return stream;
    }

    public byte[] getEncrypted() {
        return encrypted;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        stream.write(getEncrypted());
    }
}
