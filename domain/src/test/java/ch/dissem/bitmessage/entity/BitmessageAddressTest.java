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
        BitmessageAddress address = new BitmessageAddress(new PrivateKey(0, 0));
        assertNotNull(address.getPubkey());
    }
}
