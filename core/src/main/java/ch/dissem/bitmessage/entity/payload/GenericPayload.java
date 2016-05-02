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
import java.util.Arrays;

/**
 * In cases we don't know what to do with an object, we just store its bytes and send it again - we don't really
 * have to know what it is.
 */
public class GenericPayload extends ObjectPayload {
    private static final long serialVersionUID = -912314085064185940L;

    private long stream;
    private byte[] data;

    public GenericPayload(long version, long stream, byte[] data) {
        super(version);
        this.stream = stream;
        this.data = data;
    }

    public static GenericPayload read(long version, long stream, InputStream is, int length) throws IOException {
        return new GenericPayload(version, stream, Decode.bytes(is, length));
    }

    @Override
    public ObjectType getType() {
        return null;
    }

    @Override
    public long getStream() {
        return stream;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        stream.write(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GenericPayload that = (GenericPayload) o;

        if (stream != that.stream) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = (int) (stream ^ (stream >>> 32));
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }
}
