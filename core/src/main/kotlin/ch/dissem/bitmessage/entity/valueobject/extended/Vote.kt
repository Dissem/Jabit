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

package ch.dissem.bitmessage.entity.valueobject.extended

import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.InventoryVector
import ch.dissem.bitmessage.utils.Strings.str
import ch.dissem.msgpack.types.MPBinary
import ch.dissem.msgpack.types.MPMap
import ch.dissem.msgpack.types.MPString
import ch.dissem.msgpack.types.MPType
import ch.dissem.msgpack.types.Utils.mp

/**
 * Extended encoding type 'vote'. Specification still outstanding, so this will need some work.
 */
data class Vote constructor(val msgId: InventoryVector, val vote: String) : ExtendedEncoding.ExtendedType {

    override val type: String = TYPE

    override fun pack(): MPMap<MPString, MPType<*>> {
        val result = MPMap<MPString, MPType<*>>()
        result.put("".mp, TYPE.mp)
        result.put("msgId".mp, msgId.hash.mp)
        result.put("vote".mp, vote.mp)
        return result
    }

    class Builder {
        private var msgId: InventoryVector? = null
        private var vote: String? = null

        fun up(message: Plaintext): ExtendedEncoding {
            msgId = message.inventoryVector
            vote = "1"
            return ExtendedEncoding(Vote(msgId!!, vote!!))
        }

        fun down(message: Plaintext): ExtendedEncoding {
            msgId = message.inventoryVector
            vote = "-1"
            return ExtendedEncoding(Vote(msgId!!, vote!!))
        }

        fun msgId(iv: InventoryVector): Builder {
            this.msgId = iv
            return this
        }

        fun vote(vote: String): Builder {
            this.vote = vote
            return this
        }

        fun build(): ExtendedEncoding {
            return ExtendedEncoding(Vote(msgId!!, vote!!))
        }
    }

    class Unpacker : ExtendedEncoding.Unpacker<Vote> {
        override val type: String
            get() = TYPE

        override fun unpack(map: MPMap<MPString, MPType<*>>): Vote {
            val msgId = InventoryVector.fromHash((map["msgId".mp] as? MPBinary)?.value) ?: throw IllegalArgumentException("data doesn't contain proper msgId")
            val vote = str(map["vote".mp]) ?: throw IllegalArgumentException("no vote given")
            return Vote(msgId, vote)
        }
    }

    companion object {
        @JvmField
        val TYPE = "vote"
    }
}
