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

package ch.dissem.bitmessage.entity.valueobject;

import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.utils.Strings;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by chris on 13.03.15.
 */
public class InventoryVector implements Streamable {
    /**
     * Hash of the object
     */
    private final byte[] hash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventoryVector)) return false;

        InventoryVector that = (InventoryVector) o;

        return Arrays.equals(hash, that.hash);

    }

    @Override
    public int hashCode() {
        return hash != null ? Arrays.hashCode(hash) : 0;
    }

    public byte[] getHash() {
        return hash;
    }

    public InventoryVector(byte[] hash) {
        this.hash = hash;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        stream.write(hash);
    }

    @Override
    public String toString() {
        return Strings.hex(hash).toString();
    }
}
