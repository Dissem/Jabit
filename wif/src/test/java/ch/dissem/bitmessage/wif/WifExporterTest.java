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
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WifExporterTest {
    private AddressRepository repo = mock(AddressRepository.class);
    private BitmessageContext ctx;
    private WifImporter importer;
    private WifExporter exporter;

    @Before
    public void setUp() throws Exception {
        ctx = new BitmessageContext.Builder()
                .cryptography(new BouncyCryptography())
                .networkHandler(mock(NetworkHandler.class))
                .inventory(mock(Inventory.class))
                .messageRepo(mock(MessageRepository.class))
                .powRepo(mock(ProofOfWorkRepository.class))
                .nodeRegistry(mock(NodeRegistry.class))
                .addressRepo(repo)
                .build();
        importer = new WifImporter(ctx, getClass().getClassLoader().getResourceAsStream("nuked.dat"));
        assertEquals(81, importer.getIdentities().size());
        exporter = new WifExporter(ctx);
    }

    @Test
    public void testAddAll() throws Exception {
        when(repo.getIdentities()).thenReturn(importer.getIdentities());
        exporter.addAll();
        String result = exporter.toString();
        int count = 0;
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) == '[') count++;
        }
        assertEquals(importer.getIdentities().size(), count);
    }

    @Test
    public void testAddAllFromCollection() throws Exception {
        exporter.addAll(importer.getIdentities());
        String result = exporter.toString();
        int count = 0;
        for (int i = 0; i < result.length(); i++) {
            if (result.charAt(i) == '[') count++;
        }
        assertEquals(importer.getIdentities().size(), count);
    }

    @Test
    public void testAddIdentity() throws Exception {
        String expected = "[BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn]" + System.lineSeparator() +
                "label = Nuked Address" + System.lineSeparator() +
                "enabled = true" + System.lineSeparator() +
                "decoy = false" + System.lineSeparator() +
                "noncetrialsperbyte = 320" + System.lineSeparator() +
                "payloadlengthextrabytes = 14000" + System.lineSeparator() +
                "privsigningkey = 5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9" + System.lineSeparator() +
                "privencryptionkey = 5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck" + System.lineSeparator() + System.lineSeparator();
        importer = new WifImporter(ctx, expected);
        exporter.addIdentity(importer.getIdentities().get(0));
        assertEquals(expected, exporter.toString());
    }
}