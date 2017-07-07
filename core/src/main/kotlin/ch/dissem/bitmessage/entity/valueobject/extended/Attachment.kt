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

import java.io.Serializable
import java.util.*

/**
 * A "file" attachment as used by extended encoding type messages. Could either be an attachment,
 * or used inline to be used by a HTML message, for example.
 */
data class Attachment constructor(
    val name: String,
    val data: ByteArray,
    val type: String,
    val disposition: Disposition
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Attachment) return false
        return name == other.name &&
            Arrays.equals(data, other.data) &&
            type == other.type &&
            disposition == other.disposition
    }

    override fun hashCode(): Int {
        return Objects.hash(name, data, type, disposition)
    }

    enum class Disposition {
        inline, attachment
    }

    class Builder {
        private var name: String? = null
        private var data: ByteArray? = null
        private var type: String? = null
        private var disposition: Disposition? = null

        fun name(name: String): Builder {
            this.name = name
            return this
        }

        fun data(data: ByteArray): Builder {
            this.data = data
            return this
        }

        fun type(type: String): Builder {
            this.type = type
            return this
        }

        fun inline(): Builder {
            this.disposition = Disposition.inline
            return this
        }

        fun attachment(): Builder {
            this.disposition = Disposition.attachment
            return this
        }

        fun disposition(disposition: Disposition): Builder {
            this.disposition = disposition
            return this
        }

        fun build(): Attachment {
            return Attachment(name!!, data!!, type!!, disposition!!)
        }
    }
}
