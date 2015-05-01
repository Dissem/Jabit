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
import ch.dissem.bitmessage.utils.Bytes;
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

    public GetPubkey(BitmessageAddress address) {
        this.stream = address.getStream();
        if (address.getVersion() < 4)
            this.ripe = address.getRipe();
        else
            this.tag = ((V4Pubkey) address.getPubkey()).getTag();
    }

    private GetPubkey(long stream, long version, byte[] ripeOrTag) {
        this.stream = stream;
        if (version < 4) {
            ripe = ripeOrTag;
        } else {
            tag = ripeOrTag;
        }
    }

    public static GetPubkey read(InputStream is, long stream, int length, long version) throws IOException {
        return new GetPubkey(stream, version, Decode.bytes(is, length));
    }

    @Override
    public ObjectType getType() {
        return ObjectType.GET_PUBKEY;
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
