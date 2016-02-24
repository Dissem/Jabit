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
import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.utils.Base58;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.*;
import java.util.Collection;

import static ch.dissem.bitmessage.entity.valueobject.PrivateKey.PRIVATE_KEY_SIZE;
import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * @author Christian Basler
 */
public class WifExporter {
    private final BitmessageContext ctx;
    private final Ini ini;

    public WifExporter(BitmessageContext ctx) {
        this.ctx = ctx;
        this.ini = new Ini();
    }

    public WifExporter addAll() {
        for (BitmessageAddress identity : ctx.addresses().getIdentities()) {
            addIdentity(identity);
        }
        return this;
    }

    public WifExporter addAll(Collection<BitmessageAddress> identities) {
        for (BitmessageAddress identity : identities) {
            addIdentity(identity);
        }
        return this;
    }

    public WifExporter addIdentity(BitmessageAddress identity) {
        Profile.Section section = ini.add(identity.getAddress());
        section.add("label", identity.getAlias());
        section.add("enabled", true);
        section.add("decoy", false);
        section.add("noncetrialsperbyte", identity.getPubkey().getNonceTrialsPerByte());
        section.add("payloadlengthextrabytes", identity.getPubkey().getExtraBytes());
        section.add("privsigningkey", exportSecret(identity.getPrivateKey().getPrivateSigningKey()));
        section.add("privencryptionkey", exportSecret(identity.getPrivateKey().getPrivateEncryptionKey()));
        return this;
    }

    private String exportSecret(byte[] privateKey) {
        if (privateKey.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key of length 32 expected, but was " + privateKey.length);
        }
        byte[] result = new byte[37];
        result[0] = (byte) 0x80;
        System.arraycopy(privateKey, 0, result, 1, PRIVATE_KEY_SIZE);
        byte[] hash = security().doubleSha256(result, PRIVATE_KEY_SIZE + 1);
        System.arraycopy(hash, 0, result, PRIVATE_KEY_SIZE + 1, 4);
        return Base58.encode(result);
    }

    public void write(File file) throws IOException {
        file.createNewFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            write(out);
        }
    }

    public void write(OutputStream out) throws IOException {
        ini.store(out);
    }

    @Override
    public String toString() {
        StringWriter writer = new StringWriter();
        try {
            ini.store(writer);
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
        return writer.toString();
    }
}
