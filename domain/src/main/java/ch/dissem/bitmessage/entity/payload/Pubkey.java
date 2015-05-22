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
import java.util.ArrayList;

import static ch.dissem.bitmessage.utils.Security.ripemd160;
import static ch.dissem.bitmessage.utils.Security.sha512;

/**
 * Public keys for signing and encryption, the answer to a 'getpubkey' request.
 */
public abstract class Pubkey extends ObjectPayload {
    public final static long LATEST_VERSION = 4;

    public static byte[] getRipe(byte[] publicSigningKey, byte[] publicEncryptionKey) {
        return ripemd160(sha512(publicSigningKey, publicEncryptionKey));
    }

    public abstract long getVersion();

    public abstract byte[] getSigningKey();

    public abstract byte[] getEncryptionKey();

    public abstract int getBehaviorBitfield();

    public byte[] getRipe() {
        return ripemd160(sha512(getSigningKey(), getEncryptionKey()));
    }

    public long getNonceTrialsPerByte() {
        return 0;
    }

    public long getExtraBytes() {
        return 0;
    }

    public void writeUnencrypted(OutputStream out) throws IOException {
        write(out);
    }

    protected byte[] add0x04(byte[] key) {
        if (key.length == 65) return key;
        byte[] result = new byte[65];
        result[0] = 4;
        System.arraycopy(key, 0, result, 1, 64);
        return result;
    }

    /**
     * Bits 0 through 29 are yet undefined
     */
    public enum Feature {
        /**
         * Receiving node expects that the RIPE hash encoded in their address preceedes the encrypted message data of msg
         * messages bound for them.
         */
        INCLUDE_DESTINATION(1 << 30),
        /**
         * If true, the receiving node does send acknowledgements (rather than dropping them).
         */
        DOES_ACK(1 << 31);

        private int bit;

        Feature(int bit) {
            this.bit = bit;
        }

        public static int bitfield(Feature... features) {
            int bits = 0;
            for (Feature feature : features) {
                bits |= feature.bit;
            }
            return bits;
        }

        public static Feature[] features(int bitfield) {
            ArrayList<Feature> features = new ArrayList<>(Feature.values().length);
            for (Feature feature : Feature.values()) {
                if ((bitfield & feature.bit) != 0) {
                    features.add(feature);
                }
            }
            return features.toArray(new Feature[features.size()]);
        }
    }
}
