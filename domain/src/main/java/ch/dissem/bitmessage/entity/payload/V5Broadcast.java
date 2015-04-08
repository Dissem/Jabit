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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 */
public class V5Broadcast extends V4Broadcast {
    private byte[] tag;

    public V5Broadcast(long stream, byte[] tag, byte[] encrypted) {
        super(stream, encrypted);
        this.tag = tag;
    }

    public byte[] getTag() {
        return tag;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        stream.write(tag);
        super.write(stream);
    }
}
