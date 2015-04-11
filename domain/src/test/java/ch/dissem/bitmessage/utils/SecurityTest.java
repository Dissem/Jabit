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

package ch.dissem.bitmessage.utils;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.GenericPayload;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertArrayEquals;

/**
 * Created by chris on 10.04.15.
 */
public class SecurityTest {
    public static final byte[] TEST_VALUE = "teststring".getBytes();
    public static final byte[] TEST_SHA1 = DatatypeConverter.parseHexBinary(""
            + "b8473b86d4c2072ca9b08bd28e373e8253e865c4");
    public static final byte[] TEST_SHA512 = DatatypeConverter.parseHexBinary(""
            + "6253b39071e5df8b5098f59202d414c37a17d6a38a875ef5f8c7d89b0212b028"
            + "692d3d2090ce03ae1de66c862fa8a561e57ed9eb7935ce627344f742c0931d72");
    public static final byte[] TEST_RIPEMD160 = DatatypeConverter.parseHexBinary(""
            + "cd566972b5e50104011a92b59fa8e0b1234851ae");

    @Test
    public void testRipemd160() {
        assertArrayEquals(TEST_RIPEMD160, Security.ripemd160(TEST_VALUE));
    }

    @Test
    public void testSha1() {
        assertArrayEquals(TEST_SHA1, Security.sha1(TEST_VALUE));
    }

    @Test
    public void testSha512() {
        assertArrayEquals(TEST_SHA512, Security.sha512(TEST_VALUE));
    }

    @Test
    public void testChaining() {
        assertArrayEquals(TEST_SHA512, Security.sha512("test".getBytes(), "string".getBytes()));
    }

    @Test
    public void testDoubleHash() {
        assertArrayEquals(Security.sha512(TEST_SHA512), Security.doubleSha512(TEST_VALUE));
    }

    @Test(expected = IOException.class)
    public void testProofOfWorkFails() throws IOException {
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(300 + (System.currentTimeMillis() / 1000)) // 5 minutes
                .payload(new GenericPayload(1, new byte[0]))
                .build();
        Security.checkProofOfWork(objectMessage, 1000, 1000);
    }

    @Test
    public void testDoProofOfWork() throws IOException {
        Calendar expires = new GregorianCalendar();
        expires.add(1, Calendar.HOUR);
        ObjectMessage objectMessage = new ObjectMessage.Builder()
                .nonce(new byte[8])
                .expiresTime(expires.getTimeInMillis() / 1000)
                .payload(new GenericPayload(1, new byte[0]))
                .build();
        Security.doProofOfWork(objectMessage, 1000, 1000);
        Security.checkProofOfWork(objectMessage, 1000, 1000);
    }
}
