/*
 * Copyright 2016 Christian Basler
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

package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Christian Basler
 */
public class JdbcProofOfWorkRepositoryTest extends TestBase {
    private TestJdbcConfig config;
    private JdbcProofOfWorkRepository repo;

    @Before
    public void setUp() throws Exception {
        config = new TestJdbcConfig();
        config.reset();

        repo = new JdbcProofOfWorkRepository(config);

        repo.putObject(new ObjectMessage.Builder()
                        .payload(new GetPubkey(new BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn"))).build(),
                1000, 1000);
    }

    @Test
    public void ensureObjectIsStored() throws Exception {
        int sizeBefore = repo.getItems().size();
        repo.putObject(new ObjectMessage.Builder()
                        .payload(new GetPubkey(new BitmessageAddress("BM-2D9U2hv3YBMHM1zERP32anKfVKohyPN9x2"))).build(),
                1000, 1000);
        assertThat(repo.getItems().size(), is(sizeBefore + 1));
    }

    @Test
    public void ensureItemCanBeRetrieved() {
        byte[] initialHash = repo.getItems().get(0);
        ProofOfWorkRepository.Item item = repo.getItem(initialHash);
        assertThat(item, notNullValue());
        assertThat(item.object.getPayload(), instanceOf(GetPubkey.class));
        assertThat(item.nonceTrialsPerByte, is(1000L));
        assertThat(item.extraBytes, is(1000L));
    }

    @Test(expected = RuntimeException.class)
    public void ensureRetrievingNonexistingItemThrowsException() {
        repo.getItem(new byte[0]);
    }

    @Test
    public void ensureItemCanBeDeleted() {
        byte[] initialHash = repo.getItems().get(0);
        repo.removeObject(initialHash);
        assertTrue(repo.getItems().isEmpty());
    }

    @Test
    public void ensureDeletionOfNonexistingItemIsHandledSilently() {
        repo.removeObject(new byte[0]);
    }
}
