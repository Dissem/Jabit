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

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Streamable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The payload of an 'object' command. This is shared by the network.
 */
public abstract class ObjectPayload implements Streamable {
    public abstract ObjectType getType();

    public abstract long getStream();

    public boolean isSigned() {
        return false;
    }

    public void writeBytesToSign(OutputStream out) throws IOException{
        // nothing to do
    }

    /**
     * The ECDSA signature which, as of protocol v3, covers the object header starting with the time,
     * appended with the data described in this table down to the extra_bytes. Therefore, this must
     * be checked and set in the {@link ObjectMessage} object.
     */
    public byte[] getSignature() {
        return null;
    }

    public void setSignature(byte[] signature) {
        // nothing to do
    }
}
