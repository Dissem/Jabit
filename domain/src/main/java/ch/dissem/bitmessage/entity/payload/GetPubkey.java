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
 * Request for a public key.
 */
public class GetPubkey implements ObjectPayload {
    private long stream;
    private byte[] ripe;
    private byte[] tag;

    private GetPubkey(long stream, byte[] ripeOrTag) {
        this.stream = stream;
        switch (ripeOrTag.length) {
            case 20:
                ripe = ripeOrTag;
                break;
            case 32:
                tag = ripeOrTag;
                break;
            default:
                throw new RuntimeException("ripe (20 bytes) or tag (32 bytes) expected, but pubkey was " + ripeOrTag.length + " bytes long.");
        }
    }

    public static GetPubkey read(InputStream is, long stream, int length) throws IOException {
        return new GetPubkey(stream, Decode.bytes(is, length));
    }

    @Override
    public long getStream() {
        return stream;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        if (tag != null) {
            stream.write(tag);
        } else {
            stream.write(ripe);
        }
    }
}
