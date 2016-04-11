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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.payload.Pubkey.Feature;
import ch.dissem.bitmessage.entity.payload.V4Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.utils.AccessCounter;
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Encode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static ch.dissem.bitmessage.utils.Decode.bytes;
import static ch.dissem.bitmessage.utils.Decode.varInt;
import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * A Bitmessage address. Can be a user's private address, an address string without public keys or a recipient's address
 * holding private keys.
 */
public class BitmessageAddress implements Serializable {
    private static final long serialVersionUID = 2386328540805994064L;

    private final long version;
    private final long stream;
    private final byte[] ripe;
    private final byte[] tag;
    /**
     * Used for V4 address encryption. It's easier to just create it regardless of address version.
     */
    private final byte[] publicDecryptionKey;

    private String address;

    private PrivateKey privateKey;
    private Pubkey pubkey;

    private String alias;
    private boolean subscribed;
    private boolean chan;

    BitmessageAddress(long version, long stream, byte[] ripe) {
        try {
            this.version = version;
            this.stream = stream;
            this.ripe = ripe;

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Encode.varInt(version, os);
            Encode.varInt(stream, os);
            if (version < 4) {
                byte[] checksum = security().sha512(os.toByteArray(), ripe);
                this.tag = null;
                this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32);
            } else {
                // for tag and decryption key, the checksum has to be created with 0x00 padding
                byte[] checksum = security().doubleSha512(os.toByteArray(), ripe);
                this.tag = Arrays.copyOfRange(checksum, 32, 64);
                this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32);
            }
            // but for the address and its checksum they need to be stripped
            int offset = Bytes.numberOfLeadingZeros(ripe);
            os.write(ripe, offset, ripe.length - offset);
            byte[] checksum = security().doubleSha512(os.toByteArray());
            os.write(checksum, 0, 4);
            this.address = "BM-" + Base58.encode(os.toByteArray());
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public BitmessageAddress(Pubkey publicKey) {
        this(publicKey.getVersion(), publicKey.getStream(), publicKey.getRipe());
        this.pubkey = publicKey;
    }

    public BitmessageAddress(String address, String passphrase) {
        this(address);
        this.privateKey = new PrivateKey(this, passphrase);
        this.pubkey = this.privateKey.getPubkey();
        if (!Arrays.equals(ripe, privateKey.getPubkey().getRipe())) {
            throw new IllegalArgumentException("Wrong address or passphrase");
        }
    }

    public static BitmessageAddress chan(String address, String passphrase) {
        BitmessageAddress result = new BitmessageAddress(address, passphrase);
        result.chan = true;
        return result;
    }

    public static BitmessageAddress chan(long stream, String passphrase) {
        PrivateKey privateKey = new PrivateKey(Pubkey.LATEST_VERSION, stream, passphrase);
        BitmessageAddress result = new BitmessageAddress(privateKey);
        result.chan = true;
        return result;
    }

    public static List<BitmessageAddress> deterministic(String passphrase, int numberOfAddresses,
                                                        long version, long stream, boolean shorter) {
        List<BitmessageAddress> result = new ArrayList<>(numberOfAddresses);
        List<PrivateKey> privateKeys = PrivateKey.deterministic(passphrase, numberOfAddresses, version, stream, shorter);
        for (PrivateKey pk : privateKeys) {
            result.add(new BitmessageAddress(pk));
        }
        return result;
    }

    public BitmessageAddress(PrivateKey privateKey) {
        this(privateKey.getPubkey());
        this.privateKey = privateKey;
    }

    public BitmessageAddress(String address) {
        try {
            this.address = address;
            byte[] bytes = Base58.decode(address.substring(3));
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            AccessCounter counter = new AccessCounter();
            this.version = varInt(in, counter);
            this.stream = varInt(in, counter);
            this.ripe = Bytes.expand(bytes(in, bytes.length - counter.length() - 4), 20);

            // test checksum
            byte[] checksum = security().doubleSha512(bytes, bytes.length - 4);
            byte[] expectedChecksum = bytes(in, 4);
            for (int i = 0; i < 4; i++) {
                if (expectedChecksum[i] != checksum[i])
                    throw new IllegalArgumentException("Checksum of address failed");
            }
            if (version < 4) {
                checksum = security().sha512(Arrays.copyOfRange(bytes, 0, counter.length()), ripe);
                this.tag = null;
                this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32);
            } else {
                checksum = security().doubleSha512(Arrays.copyOfRange(bytes, 0, counter.length()), ripe);
                this.tag = Arrays.copyOfRange(checksum, 32, 64);
                this.publicDecryptionKey = Arrays.copyOfRange(checksum, 0, 32);
            }
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static byte[] calculateTag(long version, long stream, byte[] ripe) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Encode.varInt(version, out);
            Encode.varInt(stream, out);
            out.write(ripe);
            return Arrays.copyOfRange(security().doubleSha512(out.toByteArray()), 32, 64);
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public long getStream() {
        return stream;
    }

    public long getVersion() {
        return version;
    }

    public Pubkey getPubkey() {
        return pubkey;
    }

    public void setPubkey(Pubkey pubkey) {
        if (pubkey instanceof V4Pubkey) {
            if (!Arrays.equals(tag, ((V4Pubkey) pubkey).getTag()))
                throw new IllegalArgumentException("Pubkey has incompatible tag");
        }
        if (!Arrays.equals(ripe, pubkey.getRipe()))
            throw new IllegalArgumentException("Pubkey has incompatible ripe");
        this.pubkey = pubkey;
    }

    /**
     * @return the private key used to decrypt Pubkey objects (for v4 addresses) and broadcasts.
     */
    public byte[] getPublicDecryptionKey() {
        return publicDecryptionKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getAddress() {
        return address;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public String toString() {
        return alias == null ? address : alias;
    }

    public byte[] getRipe() {
        return ripe;
    }

    public byte[] getTag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitmessageAddress address = (BitmessageAddress) o;
        return Objects.equals(version, address.version) &&
                Objects.equals(stream, address.stream) &&
                Arrays.equals(ripe, address.ripe);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ripe);
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public boolean isChan() {
        return chan;
    }

    public void setChan(boolean chan) {
        this.chan = chan;
    }

    public boolean has(Feature feature) {
        if (pubkey == null || feature == null) {
            return false;
        }
        return feature.isActive(pubkey.getBehaviorBitfield());
    }
}
