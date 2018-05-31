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

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.entity.valueobject.InventoryVector

/**
 * The 'getdata' command is used to request objects from a node.
 */
class GetData constructor(var inventory: List<InventoryVector>) : MessagePayload {

    override val command: MessagePayload.Command = MessagePayload.Command.GETDATA

    override fun writer(): StreamableWriter = Writer(this)

    private class Writer(
        item: GetData
    ) : InventoryWriter(item.inventory)

    companion object {
        @JvmField
        val MAX_INVENTORY_SIZE = 50000
    }
}
