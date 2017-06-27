/*
 * Copyright 2017 Christian Basler
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

package ch.dissem.bitmessage.ports

import ch.dissem.bitmessage.exception.ApplicationException
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.Bytes.inc
import ch.dissem.bitmessage.utils.ThreadFactoryBuilder.Companion.pool
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * A POW engine using all available CPU cores.
 */
class MultiThreadedPOWEngine : ProofOfWorkEngine {
    private val waiterPool = Executors.newSingleThreadExecutor(pool("POW-waiter").daemon().build())
    private val workerPool = Executors.newCachedThreadPool(pool("POW-worker").daemon().build())

    /**
     * This method will block until all pending nonce calculations are done, but not wait for its own calculation
     * to finish.
     * (This implementation becomes very inefficient if multiple nonce are calculated at the same time.)

     * @param initialHash the SHA-512 hash of the object to send, sans nonce
     * *
     * @param target      the target, representing an unsigned long
     * *
     * @param callback    called with the calculated nonce as argument. The ProofOfWorkEngine implementation must make
     */
    override fun calculateNonce(initialHash: ByteArray, target: ByteArray, callback: ProofOfWorkEngine.Callback) {
        waiterPool.execute({
            val startTime = System.currentTimeMillis()

            var cores = Runtime.getRuntime().availableProcessors()
            if (cores > 255) cores = 255
            LOG.info("Doing POW using $cores cores")
            val workers = ArrayList<Worker>(cores)
            for (i in 0..cores - 1) {
                val w = Worker(cores.toByte(), i, initialHash, target)
                workers.add(w)
            }
            val futures = ArrayList<Future<ByteArray>>(cores)
            // Doing this in the previous loop might cause a ConcurrentModificationException in the worker
            // if a worker finds a nonce while new ones are still being added.
            workers.mapTo(futures) { workerPool.submit(it) }
            try {
                while (!Thread.interrupted()) {
                    futures.firstOrNull { it.isDone }?.let {
                        callback.onNonceCalculated(initialHash, it.get())
                        LOG.info("Nonce calculated in " + (System.currentTimeMillis() - startTime) / 1000 + " seconds")
                        futures.forEach { it.cancel(true) }
                        return@execute
                    }
                }
                LOG.error("POW waiter thread interrupted - this should not happen!")
            } catch (e: ExecutionException) {
                LOG.error(e.message, e)
            } catch (e: InterruptedException) {
                LOG.error("POW waiter thread interrupted - this should not happen!", e)
            }
        })
    }

    private inner class Worker internal constructor(
        private val numberOfCores: Byte, core: Int,
        private val initialHash: ByteArray,
        private val target: ByteArray
    ) : Callable<ByteArray> {
        private val mda: MessageDigest
        private val nonce = ByteArray(8)

        init {
            this.nonce[7] = core.toByte()
            try {
                mda = MessageDigest.getInstance("SHA-512")
            } catch (e: NoSuchAlgorithmException) {
                LOG.error(e.message, e)
                throw ApplicationException(e)
            }

        }

        override fun call(): ByteArray? {
            do {
                inc(nonce, numberOfCores)
                mda.update(nonce)
                mda.update(initialHash)
                if (!Bytes.lt(target, mda.digest(mda.digest()), 8)) {
                    return nonce
                }
            } while (!Thread.interrupted())
            return null
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MultiThreadedPOWEngine::class.java)
    }
}
