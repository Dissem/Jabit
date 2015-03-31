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
 * Created by chris on 24.03.15.
 */
public class GetPubkey implements ObjectPayload {
    private byte[] ripe;
    private byte[] tag;

    public GetPubkey(byte[] ripeOrTag) {
        switch (ripeOrTag.length) {
            case 20:
                ripe = ripeOrTag;
                break;
            case 32:
                tag = ripeOrTag;
                break;
            default:
                throw new RuntimeException("ripe (20 bytes) or tag (32 bytes) expected, but pubkey was " + ripeOrTag.length + " bytes.");
        }
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
