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
import ch.dissem.bitmessage.utils.Decode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Request for a public key.
 */
public class GetPubkey extends ObjectPayload {
    private static final long serialVersionUID = -3634516646972610180L;

    private long stream;
    private byte[] ripeTag;

    public GetPubkey(BitmessageAddress address) {
        super(address.getVersion());
        this.stream = address.getStream();
        if (address.getVersion() < 4)
            this.ripeTag = address.getRipe();
        else
            this.ripeTag = address.getTag();
    }

    private GetPubkey(long version, long stream, byte[] ripeOrTag) {
        super(version);
        this.stream = stream;
        this.ripeTag = ripeOrTag;
    }

    public static GetPubkey read(InputStream is, long stream, int length, long version) throws IOException {
        return new GetPubkey(version, stream, Decode.bytes(is, length));
    }

    /**
     * @return an array of bytes that represent either the ripe, or the tag of an address, depending on the
     * address version.
     */
    public byte[] getRipeTag() {
        return ripeTag;
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
        stream.write(ripeTag);
    }
}
