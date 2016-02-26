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

package ch.dissem.bitmessage.wif;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.factory.Factory;
import ch.dissem.bitmessage.utils.Base58;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * @author Christian Basler
 */
public class WifImporter {
    private static final byte WIF_FIRST_BYTE = (byte) 0x80;
    private static final int WIF_SECRET_LENGTH = 37;

    private final BitmessageContext ctx;
    private final List<BitmessageAddress> identities = new LinkedList<>();

    public WifImporter(BitmessageContext ctx, File file) throws IOException {
        this(ctx, new FileInputStream(file));
    }

    public WifImporter(BitmessageContext ctx, String data) throws IOException {
        this(ctx, new ByteArrayInputStream(data.getBytes("utf-8")));
    }

    public WifImporter(BitmessageContext ctx, InputStream in, Pubkey.Feature... features) throws IOException {
        this.ctx = ctx;

        Ini ini = new Ini();
        ini.load(in);

        for (Entry<String, Profile.Section> entry : ini.entrySet()) {
            if (!entry.getKey().startsWith("BM-"))
                continue;

            Profile.Section section = entry.getValue();
            BitmessageAddress address = Factory.createIdentityFromPrivateKey(
                    entry.getKey(),
                    getSecret(section.get("privsigningkey")),
                    getSecret(section.get("privencryptionkey")),
                    section.get("noncetrialsperbyte", long.class),
                    section.get("payloadlengthextrabytes", long.class),
                    Pubkey.Feature.bitfield(features)
            );
            address.setAlias(section.get("label"));
            identities.add(address);
        }
    }

    private byte[] getSecret(String walletImportFormat) throws IOException {
        byte[] bytes = Base58.decode(walletImportFormat);
        if (bytes[0] != WIF_FIRST_BYTE)
            throw new IOException("Unknown format: 0x80 expected as first byte, but secret " + walletImportFormat +
                    " was " + bytes[0]);
        if (bytes.length != WIF_SECRET_LENGTH)
            throw new IOException("Unknown format: " + WIF_SECRET_LENGTH +
                    " bytes expected, but secret " + walletImportFormat + " was " + bytes.length + " long");

        byte[] hash = security().doubleSha256(bytes, 33);
        for (int i = 0; i < 4; i++) {
            if (hash[i] != bytes[33 + i]) throw new IOException("Hash check failed for secret " + walletImportFormat);
        }
        return Arrays.copyOfRange(bytes, 1, 33);
    }

    public List<BitmessageAddress> getIdentities() {
        return identities;
    }

    public WifImporter importAll() {
        for (BitmessageAddress identity : identities) {
            ctx.addresses().save(identity);
        }
        return this;
    }

    public WifImporter importAll(Collection<BitmessageAddress> identities) {
        for (BitmessageAddress identity : identities) {
            ctx.addresses().save(identity);
        }
        return this;
    }

    public WifImporter importIdentity(BitmessageAddress identity) {
        ctx.addresses().save(identity);
        return this;
    }
}
