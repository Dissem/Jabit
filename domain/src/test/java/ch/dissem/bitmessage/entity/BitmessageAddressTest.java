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

import ch.dissem.bitmessage.entity.payload.V3Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.*;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class BitmessageAddressTest {
    @Test
    public void ensureAddressStaysSame() {
        String address = "BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ";
        assertEquals(address, new BitmessageAddress(address).toString());
    }

    @Test
    public void ensureStreamAndVersionAreParsed() {
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        assertEquals(1, address.getStream());
        assertEquals(3, address.getVersion());

        address = new BitmessageAddress("BM-87hJ99tPAXxtetvnje7Z491YSvbEtBJVc5e");
        assertEquals(1, address.getStream());
        assertEquals(4, address.getVersion());
    }

    @Test
    public void testCreateAddress() {
        BitmessageAddress address = new BitmessageAddress(new PrivateKey(0, 0, 0));
        assertNotNull(address.getPubkey());
    }

    @Test
    public void testV3() {
//        ripe 007402be6e76c3cb87caa946d0c003a3d4d8e1d5
//        publicSigningKey in hex: 0435e3f10f4884ec42f11f1a815ace8c7c4575cad455ca98db19a245c4c57baebdce990919b647f2657596b75aa939b858bd70c55a03492dd95119bef009cf9eea
//        publicEncryptionKey in hex: 04bf30a7ee7854f9381332a6285659215a6a4b2ab3479fa87fe996f7cd11710367748371d8d2545f8466964dd3140ab80508b2b18e45616ef6cc4d8e54db923761
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        V3Pubkey pubkey = new V3Pubkey.Builder()
                .stream(1)
                .publicSigningKey(Bytes.fromHex("0435e3f10f4884ec42f11f1a815ace8c7c4575cad455ca98db19a245c4c57baebdce990919b647f2657596b75aa939b858bd70c55a03492dd95119bef009cf9eea"))
                .publicEncryptionKey(Bytes.fromHex("04bf30a7ee7854f9381332a6285659215a6a4b2ab3479fa87fe996f7cd11710367748371d8d2545f8466964dd3140ab80508b2b18e45616ef6cc4d8e54db923761"))
                .build();
        address.setPubkey(pubkey);
        assertArrayEquals(Bytes.fromHex("007402be6e76c3cb87caa946d0c003a3d4d8e1d5"), address.getRipe());
    }

    @Test
    public void testV3PubkeyImport() throws IOException {
        ObjectMessage object = TestUtils.loadObjectMessage(3, "V3Pubkey.payload");
        V3Pubkey pubkey = (V3Pubkey) object.getPayload();
        BitmessageAddress address = new BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn");
        address.setPubkey(pubkey);
    }

    @Test
    public void testV3Import() {
        String address_string = "BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn";
        assertEquals(3, new BitmessageAddress(address_string).getVersion());
        assertEquals(1, new BitmessageAddress(address_string).getStream());

        byte[] privsigningkey = getSecret("5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9");
        byte[] privencryptionkey = getSecret("5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck");

        System.out.println("\n\n" + Strings.hex(privsigningkey) + "\n\n");

//        privsigningkey = Bytes.expand(privsigningkey, 32);
//        privencryptionkey = Bytes.expand(privencryptionkey, 32);

        BitmessageAddress address = new BitmessageAddress(new PrivateKey(privsigningkey, privencryptionkey,
                Security.createPubkey(3, 1, privsigningkey, privencryptionkey, 320, 14000)));
        assertEquals(address_string, address.getAddress());
    }

    private byte[] getSecret(String walletImportFormat) {
        byte[] bytes = Base58.decode("5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9");
        assertEquals(37, bytes.length);
        assertEquals((byte) 0x80, bytes[0]);
        byte[] checksum = Bytes.subArray(bytes, bytes.length - 4, 4);
        byte[] secret = Bytes.subArray(bytes, 1, 32);
//        assertArrayEquals("Checksum failed", checksum, Bytes.subArray(Security.doubleSha512(new byte[]{(byte) 0x80}, secret, new byte[]{0x01}), 0, 4));
        byte[] result = new byte[33];
        result[0] = 0x04;
        System.arraycopy(secret, 0, result, 1, secret.length);
        return result;
    }

    @Test
    public void testV4Import() {
        assertEquals(4, new BitmessageAddress("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke").getVersion());
        byte[] privsigningkey = Base58.decode("5KMWqfCyJZGFgW6QrnPJ6L9Gatz25B51y7ErgqNr1nXUVbtZbdU");
        byte[] privencryptionkey = Base58.decode("5JXXWEuhHQEPk414SzEZk1PHDRi8kCuZd895J7EnKeQSahJPxGz");
        BitmessageAddress address = new BitmessageAddress(new PrivateKey(privsigningkey, privencryptionkey,
                Security.createPubkey(3, 1, privsigningkey, privencryptionkey, 320, 14000)));
        assertEquals("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke", address.getAddress());
    }
}
