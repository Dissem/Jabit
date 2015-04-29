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

import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.utils.Base58;
import ch.dissem.bitmessage.utils.Bytes;
import ch.dissem.bitmessage.utils.Security;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    public void testV3Import() {
        assertEquals(3, new BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn").getVersion());
        assertEquals(1, new BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn").getStream());

        byte[] privsigningkey = Base58.decode("5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9");
        byte[] privencryptionkey = Base58.decode("5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck");
        assertEquals((byte) 0x80, privsigningkey[0]);
        assertEquals((byte) 0x80, privencryptionkey[0]);
        privsigningkey = Bytes.subArray(privsigningkey, 1, privsigningkey.length - 5);
        privencryptionkey = Bytes.subArray(privencryptionkey, 1, privencryptionkey.length - 5);

        privsigningkey = Bytes.expand(privsigningkey, 32);
        privencryptionkey = Bytes.expand(privencryptionkey, 32);

        BitmessageAddress address = new BitmessageAddress(new PrivateKey(privsigningkey, privencryptionkey,
                Security.createPubkey(3, 1, privsigningkey, privencryptionkey, 320, 14000)));
        assertEquals("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn", address.getAddress());
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
