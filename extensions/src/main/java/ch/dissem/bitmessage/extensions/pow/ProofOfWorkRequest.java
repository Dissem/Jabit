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

package ch.dissem.bitmessage.extensions.pow;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Streamable;
import ch.dissem.bitmessage.extensions.CryptoCustomMessage;
import ch.dissem.bitmessage.utils.Encode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static ch.dissem.bitmessage.utils.Decode.*;

/**
 * @author Christian Basler
 */
public class ProofOfWorkRequest implements Streamable {
    private final BitmessageAddress sender;
    private final byte[] initialHash;
    private final Request request;

    private final byte[] data;

    public ProofOfWorkRequest(BitmessageAddress sender, byte[] initialHash, Request request) {
        this(sender, initialHash, request, new byte[0]);
    }

    public ProofOfWorkRequest(BitmessageAddress sender, byte[] initialHash, Request request, byte[] data) {
        this.sender = sender;
        this.initialHash = initialHash;
        this.request = request;
        this.data = data;
    }

    public static ProofOfWorkRequest read(BitmessageAddress client, InputStream in) throws IOException {
        return new ProofOfWorkRequest(
                client,
                bytes(in, 64),
                Request.valueOf(varString(in)),
                varBytes(in)
        );
    }

    public BitmessageAddress getSender() {
        return sender;
    }

    public byte[] getInitialHash() {
        return initialHash;
    }

    public Request getRequest() {
        return request;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(initialHash);
        Encode.varString(request.name(), out);
        Encode.varBytes(data, out);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProofOfWorkRequest other = (ProofOfWorkRequest) o;

        if (!sender.equals(other.sender)) return false;
        if (!Arrays.equals(initialHash, other.initialHash)) return false;
        if (request != other.request) return false;
        return Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        int result = sender.hashCode();
        result = 31 * result + Arrays.hashCode(initialHash);
        result = 31 * result + request.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    public static class Reader implements CryptoCustomMessage.Reader<ProofOfWorkRequest> {
        private final BitmessageAddress identity;

        public Reader(BitmessageAddress identity) {
            this.identity = identity;
        }

        @Override
        public ProofOfWorkRequest read(BitmessageAddress sender, InputStream in) throws IOException {
            return ProofOfWorkRequest.read(identity, in);
        }
    }

    public enum Request {
        CALCULATE,
        CALCULATING,
        COMPLETE
    }
}
