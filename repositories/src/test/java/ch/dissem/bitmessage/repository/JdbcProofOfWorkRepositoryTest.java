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

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.InternalContext;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.ports.AddressRepository;
import ch.dissem.bitmessage.ports.MessageRepository;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository.Item;
import ch.dissem.bitmessage.utils.TestUtils;
import ch.dissem.bitmessage.utils.UnixTime;
import org.junit.Before;
import org.junit.Test;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;
import static ch.dissem.bitmessage.utils.UnixTime.MINUTE;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Christian Basler
 */
public class JdbcProofOfWorkRepositoryTest extends TestBase {
    private TestJdbcConfig config;
    private JdbcProofOfWorkRepository repo;
    private AddressRepository addressRepo;
    private MessageRepository messageRepo;

    private byte[] initialHash1;
    private byte[] initialHash2;

    @Before
    public void setUp() throws Exception {
        config = new TestJdbcConfig();
        config.reset();

        addressRepo = new JdbcAddressRepository(config);
        messageRepo = new JdbcMessageRepository(config);
        repo = new JdbcProofOfWorkRepository(config);
        InternalContext ctx = new InternalContext(new BitmessageContext.Builder()
                .addressRepo(addressRepo)
                .messageRepo(messageRepo)
                .powRepo(repo)
                .cryptography(cryptography())
        );

        repo.putObject(new ObjectMessage.Builder()
                        .payload(new GetPubkey(new BitmessageAddress("BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn"))).build(),
                1000, 1000);
        initialHash1 = repo.getItems().get(0);

        BitmessageAddress sender = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        BitmessageAddress recipient = TestUtils.loadContact();
        addressRepo.save(sender);
        addressRepo.save(recipient);
        Plaintext plaintext = new Plaintext.Builder(MSG)
                .ackData(cryptography().randomBytes(32))
                .from(sender)
                .to(recipient)
                .message("Subject", "Message")
                .status(Plaintext.Status.DOING_PROOF_OF_WORK)
                .build();
        messageRepo.save(plaintext);
        initialHash2 = cryptography().getInitialHash(plaintext.getAckMessage());
        repo.putObject(new Item(
                plaintext.getAckMessage(),
                1000, 1000,
                UnixTime.now(+10 * MINUTE),
                plaintext
        ));
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
    public void ensureAckObjectsAreStored() throws Exception {
        int sizeBefore = repo.getItems().size();
        BitmessageAddress sender = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        BitmessageAddress recipient = TestUtils.loadContact();
        addressRepo.save(sender);
        addressRepo.save(recipient);
        Plaintext plaintext = new Plaintext.Builder(MSG)
                .ackData(cryptography().randomBytes(32))
                .from(sender)
                .to(recipient)
                .message("Subject", "Message")
                .status(Plaintext.Status.DOING_PROOF_OF_WORK)
                .build();
        messageRepo.save(plaintext);
        repo.putObject(new Item(
                plaintext.getAckMessage(),
                1000, 1000,
                UnixTime.now(+10 * MINUTE),
                plaintext
        ));
        assertThat(repo.getItems().size(), is(sizeBefore + 1));
    }

    @Test
    public void ensureItemCanBeRetrieved() {
        Item item = repo.getItem(initialHash1);
        assertThat(item, notNullValue());
        assertThat(item.object.getPayload(), instanceOf(GetPubkey.class));
        assertThat(item.nonceTrialsPerByte, is(1000L));
        assertThat(item.extraBytes, is(1000L));
    }

    @Test
    public void ensureAckItemCanBeRetrieved() {
        Item item = repo.getItem(initialHash2);
        assertThat(item, notNullValue());
        assertThat(item.object.getPayload(), instanceOf(GenericPayload.class));
        assertThat(item.nonceTrialsPerByte, is(1000L));
        assertThat(item.extraBytes, is(1000L));
        assertThat(item.expirationTime, not(0));
        assertThat(item.message, notNullValue());
        assertThat(item.message.getFrom().getPrivateKey(), notNullValue());
        assertThat(item.message.getTo().getPubkey(), notNullValue());
    }

    @Test(expected = RuntimeException.class)
    public void ensureRetrievingNonexistingItemThrowsException() {
        repo.getItem(new byte[0]);
    }

    @Test
    public void ensureItemCanBeDeleted() {
        repo.removeObject(initialHash1);
        repo.removeObject(initialHash2);
        assertTrue(repo.getItems().isEmpty());
    }

    @Test
    public void ensureDeletionOfNonexistingItemIsHandledSilently() {
        repo.removeObject(new byte[0]);
    }
}
