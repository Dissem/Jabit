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

import ch.dissem.bitmessage.exception.ApplicationException;
import ch.dissem.bitmessage.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static ch.dissem.bitmessage.utils.Bytes.inc;
import static ch.dissem.bitmessage.utils.ThreadFactoryBuilder.pool;

/**
 * A POW engine using all available CPU cores.
 */
public class MultiThreadedPOWEngine implements ProofOfWorkEngine {
    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedPOWEngine.class);
    private final ExecutorService waiterPool = Executors.newSingleThreadExecutor(pool("POW-waiter").daemon().build());
    private final ExecutorService workerPool = Executors.newCachedThreadPool(pool("POW-worker").daemon().build());

    /**
     * This method will block until all pending nonce calculations are done, but not wait for its own calculation
     * to finish.
     * (This implementation becomes very inefficient if multiple nonce are calculated at the same time.)
     *
     * @param initialHash the SHA-512 hash of the object to send, sans nonce
     * @param target      the target, representing an unsigned long
     * @param callback    called with the calculated nonce as argument. The ProofOfWorkEngine implementation must make
     */
    @Override
    public void calculateNonce(final byte[] initialHash, final byte[] target, final Callback callback) {
        waiterPool.execute(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();

                int cores = Runtime.getRuntime().availableProcessors();
                if (cores > 255) cores = 255;
                LOG.info("Doing POW using " + cores + " cores");
                List<Worker> workers = new ArrayList<>(cores);
                for (int i = 0; i < cores; i++) {
                    Worker w = new Worker((byte) cores, i, initialHash, target);
                    workers.add(w);
                }
                List<Future<byte[]>> futures = new ArrayList<>(cores);
                for (Worker w : workers) {
                    // Doing this in the previous loop might cause a ConcurrentModificationException in the worker
                    // if a worker finds a nonce while new ones are still being added.
                    futures.add(workerPool.submit(w));
                }
                try {
                    while (!Thread.interrupted()) {
                        for (Future<byte[]> future : futures) {
                            if (future.isDone()) {
                                callback.onNonceCalculated(initialHash, future.get());
                                LOG.info("Nonce calculated in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
                                for (Future<byte[]> f : futures) {
                                    f.cancel(true);
                                }
                                return;
                            }
                        }
                        Thread.sleep(100);
                    }
                    LOG.error("POW waiter thread interrupted - this should not happen!");
                } catch (ExecutionException e) {
                    LOG.error(e.getMessage(), e);
                } catch (InterruptedException e) {
                    LOG.error("POW waiter thread interrupted - this should not happen!", e);
                }
            }
        });
    }

    private class Worker implements Callable<byte[]> {
        private final byte numberOfCores;
        private final byte[] initialHash;
        private final byte[] target;
        private final MessageDigest mda;
        private final byte[] nonce = new byte[8];

        Worker(byte numberOfCores, int core, byte[] initialHash, byte[] target) {
            this.numberOfCores = numberOfCores;
            this.initialHash = initialHash;
            this.target = target;
            this.nonce[7] = (byte) core;
            try {
                mda = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                LOG.error(e.getMessage(), e);
                throw new ApplicationException(e);
            }
        }

        @Override
        public byte[] call() throws Exception {
            do {
                inc(nonce, numberOfCores);
                mda.update(nonce);
                mda.update(initialHash);
                if (!Bytes.lt(target, mda.digest(mda.digest()), 8)) {
                    return nonce;
                }
            } while (!Thread.interrupted());
            return null;
        }
    }
}
