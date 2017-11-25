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

package ch.dissem.bitmessage.utils

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.InternalContext
import ch.dissem.bitmessage.Preferences
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.ObjectMessage
import ch.dissem.bitmessage.entity.payload.V4Pubkey
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.entity.valueobject.PrivateKey
import ch.dissem.bitmessage.exception.DecryptionFailedException
import ch.dissem.bitmessage.factory.Factory
import ch.dissem.bitmessage.ports.*
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import org.junit.Assert.assertEquals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Random
import kotlin.NoSuchElementException

/**
 * If there's ever a need for this in production code, it should be rewritten to be more efficient.
 */
object TestUtils {
    @JvmField
    val RANDOM = Random()

    @JvmStatic
    fun int16(number: Int): ByteArray {
        val out = ByteArrayOutputStream()
        Encode.int16(number, out)
        return out.toByteArray()
    }

    @JvmStatic
    fun loadObjectMessage(version: Int, resourceName: String): ObjectMessage {
        val data = getBytes(resourceName)
        val input = ByteArrayInputStream(data)
        return Factory.getObjectMessage(version, input, data.size) ?: throw NoSuchElementException("error loading object message")
    }

    @JvmStatic
    fun getBytes(resourceName: String): ByteArray {
        val input = javaClass.classLoader.getResourceAsStream(resourceName)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len = input.read(buffer)
        while (len != -1) {
            out.write(buffer, 0, len)
            len = input.read(buffer)
        }
        return out.toByteArray()
    }

    @JvmStatic
    fun randomInventoryVector(): InventoryVector {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return InventoryVector(bytes)
    }

    @JvmStatic
    fun getResource(resourceName: String): InputStream =
        javaClass.classLoader.getResourceAsStream(resourceName)

    @JvmStatic
    fun loadIdentity(address: String): BitmessageAddress {
        val privateKey = PrivateKey.read(TestUtils.getResource(address + ".privkey"))
        val identity = BitmessageAddress(privateKey)
        assertEquals(address, identity.address)
        return identity
    }

    @Throws(DecryptionFailedException::class)
    @JvmStatic
    fun loadContact(): BitmessageAddress {
        val address = BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h")
        val objectMessage = TestUtils.loadObjectMessage(3, "V4Pubkey.payload")
        objectMessage.decrypt(address.publicDecryptionKey)
        address.pubkey = objectMessage.payload as V4Pubkey
        return address
    }

    @JvmStatic
    fun loadPubkey(address: BitmessageAddress) {
        val bytes = getBytes(address.address + ".pubkey")
        val pubkey = Factory.readPubkey(address.version, address.stream, ByteArrayInputStream(bytes), bytes.size, false)
        address.pubkey = pubkey
    }

    @JvmStatic
    fun mockedInternalContext(
        cryptography: Cryptography = mock {},
        inventory: Inventory = mock {},
        nodeRegistry: NodeRegistry = mock {},
        networkHandler: NetworkHandler = mock {},
        addressRepository: AddressRepository = mock {},
        messageRepository: MessageRepository = mock {},
        proofOfWorkRepository: ProofOfWorkRepository = mock {},
        proofOfWorkEngine: ProofOfWorkEngine = mock {},
        customCommandHandler: CustomCommandHandler = mock {},
        listener: BitmessageContext.Listener = mock {},
        labeler: Labeler = mock {},
        port: Int = 0,
        connectionTTL: Long = 0,
        connectionLimit: Int = 0
    ): InternalContext {
        return spy(InternalContext(
            cryptography,
            inventory,
            nodeRegistry,
            networkHandler,
            addressRepository,
            messageRepository,
            proofOfWorkRepository,
            proofOfWorkEngine,
            customCommandHandler,
            listener,
            labeler,
            Preferences().apply {
                this.userAgent = "/Jabit:TEST/"
                this.port = port
                this.connectionTTL = connectionTTL
                this.connectionLimit = connectionLimit
            }
        ))
    }
}
