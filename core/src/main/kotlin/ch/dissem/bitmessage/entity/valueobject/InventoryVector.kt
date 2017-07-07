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

package ch.dissem.bitmessage.entity.valueobject

import ch.dissem.bitmessage.entity.Streamable
import ch.dissem.bitmessage.utils.Strings
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*

data class InventoryVector constructor(
    /**
     * Hash of the object
     */
    val hash: ByteArray) : Streamable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InventoryVector) return false

        return Arrays.equals(hash, other.hash)
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(hash)
    }

    override fun write(out: OutputStream) {
        out.write(hash)
    }

    override fun write(buffer: ByteBuffer) {
        buffer.put(hash)
    }

    override fun toString(): String {
        return Strings.hex(hash)
    }

    companion object {
        @JvmStatic fun fromHash(hash: ByteArray?): InventoryVector? {
            return InventoryVector(
                hash ?: return null
            )
        }
    }
}
