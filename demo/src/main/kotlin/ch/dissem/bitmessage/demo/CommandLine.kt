/*
 * Copyright 2016 Christian Basler
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

package ch.dissem.bitmessage.demo

import ch.dissem.bitmessage.entity.BitmessageAddress
import java.util.*

class CommandLine {

    companion object {
        val COMMAND_BACK = "b) back"
        val ERROR_UNKNOWN_COMMAND = "Unknown command. Please try again."
    }

    private val scanner: Scanner by lazy { Scanner(System.`in`) }

    fun nextCommand() = scanner.nextLine().trim().toLowerCase()

    fun nextLine(): String = scanner.nextLine()

    fun nextLineTrimmed(): String = scanner.nextLine().trim()

    fun yesNo(question: String): Boolean {
        var answer: String
        while (true) {
            println("$question (y/n)")
            answer = nextLine()
            when (answer.toLowerCase()) {
                "y" -> return true
                "n" -> return false
            }
        }
    }

    fun selectAddress(addresses: List<BitmessageAddress>, label: String): BitmessageAddress? {
        if (addresses.size == 1) {
            return addresses[0]
        }

        var command: String
        do {
            println()
            println(label)

            listAddresses(addresses, "contacts")
            println(COMMAND_BACK)

            command = nextCommand()
            when (command) {
                "b" -> return null
                else -> try {
                    val index = command.toInt() - 1
                    return addresses[index]
                } catch (e: NumberFormatException) {
                    println(ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
        return null
    }

    fun listAddresses(addresses: List<BitmessageAddress>, kind: String) {
        if (addresses.isEmpty()) {
            println("You have no $kind yet.")
        } else {
            addresses.forEachIndexed { index, address ->
                print("$index) ")
                if (address.alias == null) {
                    println(address.address)
                } else {
                    println("${address.alias} (${address.address})")
                }
            }
        }
    }
}
