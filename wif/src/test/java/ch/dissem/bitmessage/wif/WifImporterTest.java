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
import ch.dissem.bitmessage.ports.*;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class WifImporterTest {
    private AddressRepository repo = mock(AddressRepository.class);
    private BitmessageContext ctx;
    private WifImporter importer;

    @Before
    public void setUp() throws Exception {
        ctx = new BitmessageContext.Builder()
                .networkHandler(mock(NetworkHandler.class))
                .inventory(mock(Inventory.class))
                .messageRepo(mock(MessageRepository.class))
                .nodeRegistry(mock(NodeRegistry.class))
                .addressRepo(repo)
                .build();
        importer = new WifImporter(ctx, getClass().getClassLoader().getResourceAsStream("nuked.dat"));
    }


    @Test
    public void testImportSingleIdentity() throws Exception {
        importer = new WifImporter(ctx, "[BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci]\n" +
                "label = Nuked Address\n" +
                "enabled = true\n" +
                "decoy = false\n" +
                "noncetrialsperbyte = 320\n" +
                "payloadlengthextrabytes = 14000\n" +
                "privsigningkey = 5JU5t2JA58sP5aJwKAcrYg5EpBA9bJPrBSaFfaZ7ogmwTMDCfHL\n" +
                "privencryptionkey = 5Kkx5MwjQcM4kyduKvCEPM6nVNynMdRcg88VQ5iVDWUekMz1igH");
        assertEquals(1, importer.getIdentities().size());
        BitmessageAddress identity = importer.getIdentities().get(0);
        assertEquals("BM-2cWJ4UFRTCehWuWNsW8fJkAYMxU4S8jxci", identity.getAddress());
        assertEquals("Nuked Address", identity.getAlias());
        assertEquals(320, identity.getPubkey().getNonceTrialsPerByte());
        assertEquals(14000, identity.getPubkey().getExtraBytes());
        assertNotNull("Private key", identity.getPrivateKey());
        assertEquals(32, identity.getPrivateKey().getPrivateEncryptionKey().length);
        assertEquals(32, identity.getPrivateKey().getPrivateSigningKey().length);
    }

    @Test
    public void testGetIdentities() throws Exception {
        List<BitmessageAddress> identities = importer.getIdentities();
        assertEquals(81, identities.size());
    }

    @Test
    public void testImportAll() throws Exception {
        importer.importAll();
        verify(repo, times(81)).save(any(BitmessageAddress.class));
    }

    @Test
    public void testImportAllFromCollection() throws Exception {
        List<BitmessageAddress> identities = importer.getIdentities();
        importer.importAll(identities);
        for (BitmessageAddress identity : identities) {
            verify(repo, times(1)).save(identity);
        }
    }

    @Test
    public void testImportIdentity() throws Exception {
        List<BitmessageAddress> identities = importer.getIdentities();
        importer.importIdentity(identities.get(0));
        verify(repo, times(1)).save(identities.get(0));
    }
}