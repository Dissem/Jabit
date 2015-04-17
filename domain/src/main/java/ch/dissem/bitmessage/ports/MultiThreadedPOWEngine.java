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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static ch.dissem.bitmessage.utils.Bytes.inc;

/**
 * Created by chris on 14.04.15.
 */
public class MultiThreadedPOWEngine implements ProofOfWorkEngine {
    private static Logger LOG = LoggerFactory.getLogger(MultiThreadedPOWEngine.class);

    @Override
    public byte[] calculateNonce(byte[] initialHash, byte[] target) {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > 255) cores = 255;
        LOG.info("Doing POW using " + cores + " cores");
        long time = System.currentTimeMillis();
        List<Worker> workers = new ArrayList<>(cores);
        for (int i = 0; i < cores; i++) {
            Worker w = new Worker(workers, (byte) cores, i, initialHash, target);
            workers.add(w);
        }
        for (Worker w : workers) {
            w.start();
        }
        for (Worker w : workers) {
            try {
                w.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (w.isSuccessful()) {
                LOG.info("Nonce calculated in " + ((System.currentTimeMillis() - time) / 1000) + " seconds");
                return w.getNonce();
            }
        }
        throw new RuntimeException("All workers ended without yielding a nonce - something is seriously broken!");
    }

    private static class Worker extends Thread {
        private final byte numberOfCores;
        private final List<Worker> workers;
        private final byte[] initialHash;
        private final byte[] target;
        private final MessageDigest mda;
        private final byte[] nonce = new byte[8];
        private boolean successful = false;

        public Worker(List<Worker> workers, byte numberOfCores, int core, byte[] initialHash, byte[] target) {
            this.numberOfCores = numberOfCores;
            this.workers = workers;
            this.initialHash = initialHash;
            this.target = target;
            this.nonce[7] = (byte) core;
            try {
                mda = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        public boolean isSuccessful() {
            return successful;
        }

        public byte[] getNonce() {
            return nonce;
        }

        @Override
        public void run() {
            do {
                inc(nonce, numberOfCores);
                mda.update(nonce);
                mda.update(initialHash);
                if (!Bytes.lt(target, mda.digest(mda.digest()), 8)) {
                    successful = true;
                    for (Worker w : workers) {
                        w.interrupt();
                    }
                    return;
                }
            } while (!Thread.interrupted());
        }
    }
}
