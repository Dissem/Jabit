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

import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by chris on 27.03.15.
 */
public class V3Pubkey extends V2Pubkey {
    long nonceTrialsPerByte;
    long extraBytes;
    byte[] signature;

    @Override
    public void write(OutputStream stream) throws IOException {
        super.write(stream);
        Encode.varInt(nonceTrialsPerByte, stream);
        Encode.varInt(extraBytes, stream);
        Encode.varInt(signature.length, stream);
        stream.write(signature);
    }

    @Override
    public long getVersion() {
        return 3;
    }
}
