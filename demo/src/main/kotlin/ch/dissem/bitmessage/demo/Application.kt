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

package ch.dissem.bitmessage.demo

import ch.dissem.bitmessage.BitmessageContext
import ch.dissem.bitmessage.demo.CommandLine.Companion.COMMAND_BACK
import ch.dissem.bitmessage.demo.CommandLine.Companion.ERROR_UNKNOWN_COMMAND
import ch.dissem.bitmessage.entity.BitmessageAddress
import ch.dissem.bitmessage.entity.Plaintext
import ch.dissem.bitmessage.entity.Plaintext.Type.BROADCAST
import ch.dissem.bitmessage.entity.Plaintext.Type.MSG
import ch.dissem.bitmessage.entity.payload.Pubkey
import ch.dissem.bitmessage.entity.valueobject.Label
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import org.apache.commons.lang3.text.WordUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import kotlin.text.RegexOption.IGNORE_CASE

/**
 * A simple command line Bitmessage application
 */
class Application(
    ctxBuilder: BitmessageContext.Builder,
    syncServer: InetAddress? = null,
    syncPort: Int = 8444
) {
    private val log: Logger by lazy { LoggerFactory.getLogger(Application::class.java) }
    private val responsePattern: Regex by lazy { Regex("^RE:.*\$", IGNORE_CASE) }
    private val commandLine: CommandLine by lazy { CommandLine() }

    private val ctx: BitmessageContext

    init {
        ctx = ctxBuilder
            .listener { plaintext -> println("New Message from ${plaintext.from}: ${plaintext.subject}") }
            .build()

        if (syncServer == null) {
            ctx.startup()
        }

        var command: String
        do {
            println()
            println("available commands:")
            println("i) identities")
            println("c) contacts")
            println("s) subscriptions")
            println("m) messages")
            if (syncServer != null) {
                println("y) sync")
            }
            println("?) info")
            println("e) exit")

            command = commandLine.nextLine()
            try {
                when (command) {
                    "i" -> identities()
                    "c" -> contacts()
                    "s" -> subscriptions()
                    "m" -> labels()
                    "?" -> info()
                    "e" -> {} //noop
                    "y" -> {
                        if (syncServer != null) {
                            ctx.synchronize(syncServer, syncPort, 120, true)
                        }
                    }
                    else -> println(ERROR_UNKNOWN_COMMAND)
                }
            } catch (e: Exception) {
                log.debug(e.message)
            }
        } while ("e" != command)
        log.info("Shutting down client")
        ctx.cleanup()
        ctx.shutdown()
    }

    private fun info() {
        var command: String
        do {
            println()
            println(ctx.status())
            println()
            println("c) cleanup inventory")
            println("r) resend unacknowledged messages")
            println(COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "c" -> ctx.cleanup()
                "r" -> ctx.resendUnacknowledgedMessages()
                "b" -> return
            }
        } while ("b" != command)
    }

    private fun identities() {
        var command: String
        var identities = ctx.addresses.getIdentities()
        do {
            println()
            commandLine.listAddresses(identities, "identities")
            println("a) create identity")
            println("c) join chan")
            println(COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "a" -> {
                    addIdentity()
                    identities = ctx.addresses.getIdentities()
                }
                "c" -> {
                    joinChan()
                    identities = ctx.addresses.getIdentities()
                }
                "b" -> return
                else -> try {
                    val index = command.toInt()
                    address(identities[index])
                } catch (e: NumberFormatException) {
                    println(ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
    }

    private fun addIdentity() {
        println()
        val identity = ctx.createIdentity(commandLine.yesNo("would you like a shorter address? This will take some time to calculate."), Pubkey.Feature.DOES_ACK)
        println("Please enter an alias for this identity, or an empty string for none")
        val alias = commandLine.nextLineTrimmed()
        if (alias.isNotEmpty()) {
            identity.alias = alias
        }
        ctx.addresses.save(identity)
    }

    private fun joinChan() {
        println()
        println("Passphrase: ")
        val passphrase = commandLine.nextLine()
        println("Address: ")
        val address = commandLine.nextLineTrimmed()
        ctx.joinChan(passphrase, address)
    }

    private fun contacts() {
        var command: String
        var contacts = ctx.addresses.getContacts()
        do {
            println()
            commandLine.listAddresses(contacts, "contacts")
            println()
            println("a) add contact")
            println(COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "a" -> {
                    addContact(false)
                    contacts = ctx.addresses.getContacts()
                }
                "b" -> return
                else -> try {
                    val index = command.toInt()
                    address(contacts[index])
                } catch (e: NumberFormatException) {
                    println(ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
    }

    private fun addContact(isSubscription: Boolean) {
        println()
        println("Please enter the Bitmessage address you want to add")
        try {
            val address = BitmessageAddress(commandLine.nextLineTrimmed())
            println("Please enter an alias for this address, or an empty string for none")
            val alias = commandLine.nextLineTrimmed()
            when {
                alias.isNotEmpty() -> address.alias = alias
                isSubscription -> ctx.addSubscribtion(address)
            }
            ctx.addContact(address)
        } catch (e: IllegalArgumentException) {
            println(e.message)
        }
    }

    private fun subscriptions() {
        var command: String
        var subscriptions = ctx.addresses.getSubscriptions()
        do {
            println()
            commandLine.listAddresses(subscriptions, "subscriptions")
            println()
            println("a) add subscription")
            println(COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "a" -> {
                    addContact(true)
                    subscriptions = ctx.addresses.getSubscriptions()
                }
                "b" -> return
                else -> try {
                    val index = command.toInt()
                    address(subscriptions[index])
                } catch (e: NumberFormatException) {
                    println(ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
    }

    private fun address(address: BitmessageAddress) {
        println()
        if (address.alias != null) {
            println(address.alias)
        }
        println(address.address)
        println("Stream:  ${address.stream}")
        println("Version: ${address.version}")
        if (address.privateKey == null) {
            if (address.pubkey == null) {
                println("Public key still missing")
            } else {
                println("Public key available")
            }
        } else {
            if (address.isChan) {
                println("Chan")
            } else {
                println("Identity")
            }
        }
    }

    private fun labels() {
        val labels = ctx.messages.getLabels()
        var command: String
        do {
            println()
            labels.forEachIndexed { index, label ->
                print("$index) $label")
                val unread = ctx.messages.countUnread(label)
                if (unread > 0) {
                    println(" [$unread]")
                } else {
                    println()
                }
            }
            println("a) Archive")
            println()
            println("c) compose message")
            println("s) compose broadcast")
            println(COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "a" -> messages(null)
                "c" -> compose(false)
                "s" -> compose(true)
                "b" -> return
                else -> try {
                    val index = command.toInt()
                    messages(labels[index])
                } catch (e: NumberFormatException) {
                    println(ERROR_UNKNOWN_COMMAND)
                } catch (e: IndexOutOfBoundsException) {
                    println(ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
    }

    private fun messages(label: Label?) {
        var command: String
        do {
            val messages = ctx.messages.findMessages(label)
            println()
            if (messages.isEmpty()) {
                println("There are no messages.")
            }
            messages.forEachIndexed { index, message ->
                println("$index" + (if (message.isUnread()) ">" else ")") + " From: ${message.from} ; Subject: ${message.subject}")
            }
            println()
            println("c) compose message")
            println("s) compose broadcast")
            if (label != null && label.type === Label.Type.TRASH) {
                println("e) empty trash")
            }
            println(CommandLine.COMMAND_BACK)

            command = commandLine.nextCommand()
            when (command) {
                "c" -> compose(false)
                "s" -> compose(true)
                "e" -> {
                    messages.forEach { ctx.messages.remove(it) }
                    return
                }
                "b" -> return
                else -> try {
                    val index = command.toInt()
                    show(messages[index])
                } catch (e: NumberFormatException) {
                    println(CommandLine.ERROR_UNKNOWN_COMMAND)
                } catch (e: IndexOutOfBoundsException) {
                    println(CommandLine.ERROR_UNKNOWN_COMMAND)
                }
            }
        } while ("b" != command)
    }

    private fun show(message: Plaintext) {
        println()
        println("From:    ${message.from}")
        println("To:      ${message.to}")
        println("Subject: ${message.subject}")
        println()
        println(WordUtils.wrap(message.text, 120))
        println()
        println(message.labels.joinToString(", ", "Labels: ") { it.toString() })
        println()
        ctx.labeler.markAsRead(message)
        ctx.messages.save(message)

        var command: String
        do {
            println("r) reply")
            println("d) delete")
            println("a) archive")
            println(COMMAND_BACK)
            command = commandLine.nextCommand()
            when (command) {
                //TODO: What should be done if 'to' is null? Also: Are 'to' and 'from' in the right order here
                "r" -> compose(message.to!!, message.from, message)
                "d" -> {
                    ctx.labeler.delete(message)
                    ctx.messages.save(message)
                    return
                }
                "a" -> {
                    ctx.labeler.archive(message)
                    ctx.messages.save(message)
                    return
                }
                "b" -> return
                else -> println(ERROR_UNKNOWN_COMMAND)
            }
        } while ("b" != command)
    }

    private fun compose(broadcast: Boolean) {
        println()
        val from = selectIdentity() ?: return
        val to = if (broadcast) null else selectContact()
        if (!broadcast && to == null) {
            return
        }
        compose(from, to, null)
    }

    private fun selectIdentity(): BitmessageAddress? {
        var addresses = ctx.addresses.getIdentities()
        while (addresses.isEmpty()) {
            addIdentity()
            addresses = ctx.addresses.getIdentities()
        }
        return commandLine.selectAddress(addresses, "From:")
    }

    private fun selectContact(): BitmessageAddress? {
        var addresses = ctx.addresses.getContacts()
        while (addresses.isEmpty()) {
            addContact(false)
            addresses = ctx.addresses.getContacts()
        }
        return commandLine.selectAddress(addresses, "To:")
    }

    private fun compose(from: BitmessageAddress, to: BitmessageAddress?, parent: Plaintext?) {
        val broadcast = to == null
        val subject: String
        println()
        println("From:    $from")
        if (!broadcast) {
            println("To:      $to")
        }
        if (parent != null) {
            subject = if (responsePattern.matches(parent.subject!!)) {
                //TODO: Is this truly safe?
                parent.subject!!
            } else {
                "RE: ${parent.subject}"
            }
            println("Subject: $subject")
        } else {
            print("Subject: ")
            subject = commandLine.nextLineTrimmed()
        }
        println("Message:")

        val message = buildString {
            var line: String
            do {
                line = commandLine.nextLine()
                append(line)
            } while (line.isNotEmpty() || !commandLine.yesNo("Send message?"))
        }

        val type = if (broadcast) BROADCAST else MSG
        val builder = Plaintext.Builder(type)
        builder.from(from)
        builder.to(to)

        if (commandLine.yesNo("Use extended encoding?")) {
            val extended = Message.Builder()
            extended.subject(subject).body(message)
            if (parent != null) {
                extended.addParent(parent)
            }
            builder.message(extended.build())
        } else {
            builder.message(subject, message)
        }
        ctx.send(builder.build())
    }
}
