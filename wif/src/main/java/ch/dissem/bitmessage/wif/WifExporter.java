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
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Security;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.*;
import java.util.Collection;

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

    public void addAll() {
        for (BitmessageAddress identity : ctx.addresses().getIdentities()) {
            addIdentity(identity);
        }
    }

    public void addAll(Collection<BitmessageAddress> identities) {
        for (BitmessageAddress identity : identities) {
            addIdentity(identity);
        }
    }

    public void addIdentity(BitmessageAddress identity) {
        Profile.Section section = ini.add(identity.getAddress());
        section.add("label", identity.getAlias());
        section.add("enabled", true);
        section.add("decoy", false);
        section.add("noncetrialsperbyte", identity.getPubkey().getNonceTrialsPerByte());
        section.add("payloadlengthextrabytes", identity.getPubkey().getExtraBytes());
        section.add("privsigningkey", exportSecret(identity.getPrivateKey().getPrivateSigningKey()));
        section.add("privencryptionkey", exportSecret(identity.getPrivateKey().getPrivateEncryptionKey()));
    }

    private String exportSecret(byte[] privateKey) {
        if (privateKey.length != 32) {
            throw new IllegalArgumentException("Private key of length 32 expected, but was " + privateKey.length);
        }
        byte[] result = new byte[37];
        result[0] = (byte) 0x80;
        System.arraycopy(privateKey, 0, result, 1, 32);
        byte[] hash = Security.doubleSha256(result, 33);
        System.arraycopy(hash, 0, result, 33, 4);
        return Base58.encode(result);
    }

    public void write(File file) throws IOException {
        write(new FileOutputStream(file));
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
            throw new RuntimeException(e);
        }
        return writer.toString();
    }
}
