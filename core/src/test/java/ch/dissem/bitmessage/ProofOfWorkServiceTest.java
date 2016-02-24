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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static ch.dissem.bitmessage.entity.Plaintext.Type.MSG;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.*;

/**
 * @author Christian Basler
 */
public class ProofOfWorkServiceTest {
    private ProofOfWorkService proofOfWorkService;

    private Cryptography cryptography;
    @Mock
    private InternalContext ctx;
    @Mock
    private ProofOfWorkRepository proofOfWorkRepo;
    @Mock
    private Inventory inventory;
    @Mock
    private NetworkHandler networkHandler;
    @Mock
    private MessageRepository messageRepo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        cryptography = spy(new BouncyCryptography());
        Singleton.initialize(cryptography);

        ctx = mock(InternalContext.class);
        when(ctx.getProofOfWorkRepository()).thenReturn(proofOfWorkRepo);
        when(ctx.getInventory()).thenReturn(inventory);
        when(ctx.getNetworkHandler()).thenReturn(networkHandler);
        when(ctx.getMessageRepository()).thenReturn(messageRepo);

        proofOfWorkService = new ProofOfWorkService();
        proofOfWorkService.setContext(ctx);
    }

    @Test
    public void ensureMissingProofOfWorkIsDone() {
        when(proofOfWorkRepo.getItems()).thenReturn(Arrays.asList(new byte[64]));
        when(proofOfWorkRepo.getItem(any(byte[].class))).thenReturn(new ProofOfWorkRepository.Item(null, 1001, 1002));
        doNothing().when(cryptography).doProofOfWork(any(ObjectMessage.class), anyLong(), anyLong(), any(ProofOfWorkEngine.Callback.class));

        proofOfWorkService.doMissingProofOfWork();

        verify(cryptography).doProofOfWork((ObjectMessage) isNull(), eq(1001L), eq(1002L),
                any(ProofOfWorkEngine.Callback.class));
    }

    @Test
    public void ensureCalculatedNonceIsStored() throws Exception {
        BitmessageAddress identity = TestUtils.loadIdentity("BM-2cSqjfJ8xK6UUn5Rw3RpdGQ9RsDkBhWnS8");
        BitmessageAddress address = TestUtils.loadContact();
        Plaintext plaintext = new Plaintext.Builder(MSG).from(identity).to(address).message("", "").build();
        ObjectMessage object = new ObjectMessage.Builder()
                .payload(new Msg(plaintext))
                .build();
        object.sign(identity.getPrivateKey());
        object.encrypt(address.getPubkey());
        byte[] initialHash = new byte[64];
        byte[] nonce = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        when(proofOfWorkRepo.getItem(initialHash)).thenReturn(new ProofOfWorkRepository.Item(object, 1001, 1002));
        when(messageRepo.getMessage(initialHash)).thenReturn(plaintext);

        proofOfWorkService.onNonceCalculated(initialHash, nonce);

        verify(proofOfWorkRepo).removeObject(eq(initialHash));
        verify(inventory).storeObject(eq(object));
        verify(networkHandler).offer(eq(object.getInventoryVector()));
        assertThat(plaintext.getInventoryVector(), equalTo(object.getInventoryVector()));
    }
}
