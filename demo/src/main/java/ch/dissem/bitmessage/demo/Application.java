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

package ch.dissem.bitmessage.demo;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.cryptography.bc.BouncyCryptography;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.valueobject.Label;
import ch.dissem.bitmessage.networking.DefaultNetworkHandler;
import ch.dissem.bitmessage.ports.MemoryNodeRegistry;
import ch.dissem.bitmessage.repository.*;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import static ch.dissem.bitmessage.demo.CommandLine.COMMAND_BACK;
import static ch.dissem.bitmessage.demo.CommandLine.ERROR_UNKNOWN_COMMAND;

/**
 * A simple command line Bitmessage application
 */
public class Application {
    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private final CommandLine commandLine;

    private BitmessageContext ctx;

    public Application(InetAddress syncServer, int syncPort) {
        JdbcConfig jdbcConfig = new JdbcConfig();
        ctx = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(jdbcConfig))
                .inventory(new JdbcInventory(jdbcConfig))
                .nodeRegistry(new MemoryNodeRegistry())
                .messageRepo(new JdbcMessageRepository(jdbcConfig))
                .powRepo(new JdbcProofOfWorkRepository(jdbcConfig))
                .networkHandler(new DefaultNetworkHandler())
                .cryptography(new BouncyCryptography())
                .port(48444)
                .listener(plaintext -> System.out.println("New Message from " + plaintext.getFrom() + ": " + plaintext.getSubject()))
                .build();

        if (syncServer == null) {
            ctx.startup();
        }

        commandLine = new CommandLine();

        String command;
        do {
            System.out.println();
            System.out.println("available commands:");
            System.out.println("i) identities");
            System.out.println("c) contacts");
            System.out.println("s) subscriptions");
            System.out.println("m) messages");
            if (syncServer != null) {
                System.out.println("y) sync");
            }
            System.out.println("?) info");
            System.out.println("e) exit");

            command = commandLine.nextCommand();
            try {
                switch (command) {
                    case "i": {
                        identities();
                        break;
                    }
                    case "c":
                        contacts();
                        break;
                    case "s":
                        subscriptions();
                        break;
                    case "m":
                        labels();
                        break;
                    case "?":
                        info();
                        break;
                    case "e":
                        break;
                    case "y":
                        if (syncServer != null) {
                            ctx.synchronize(syncServer, syncPort, 120, true);
                        }
                        break;
                    default:
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                }
            } catch (Exception e) {
                LOG.debug(e.getMessage());
            }
        } while (!"e".equals(command));
        LOG.info("Shutting down client");
        ctx.cleanup();
        ctx.shutdown();
    }

    private void info() {
        System.out.println();
        System.out.println(ctx.status());
    }

    private void identities() {
        String command;
        List<BitmessageAddress> identities = ctx.addresses().getIdentities();
        do {
            System.out.println();
            commandLine.listAddresses(identities, "identities");
            System.out.println("a) create identity");
            System.out.println("c) join chan");
            System.out.println(COMMAND_BACK);

            command = commandLine.nextCommand();
            switch (command) {
                case "a":
                    addIdentity();
                    identities = ctx.addresses().getIdentities();
                    break;
                case "c":
                    joinChan();
                    identities = ctx.addresses().getIdentities();
                    break;
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        address(identities.get(index));
                    } catch (NumberFormatException e) {
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equals(command));
    }

    private void addIdentity() {
        System.out.println();
        BitmessageAddress identity = ctx.createIdentity(commandLine.yesNo("would you like a shorter address? This will take some time to calculate."), Pubkey.Feature.DOES_ACK);
        System.out.println("Please enter an alias for this identity, or an empty string for none");
        String alias = commandLine.nextLineTrimmed();
        if (alias.length() > 0) {
            identity.setAlias(alias);
        }
        ctx.addresses().save(identity);
    }

    private void joinChan() {
        System.out.println();
        System.out.print("Passphrase: ");
        String passphrase = commandLine.nextLine();
        System.out.print("Address: ");
        String address = commandLine.nextLineTrimmed();
        ctx.joinChan(passphrase, address);
    }

    private void contacts() {
        String command;
        List<BitmessageAddress> contacts = ctx.addresses().getContacts();
        do {
            System.out.println();
            commandLine.listAddresses(contacts, "contacts");
            System.out.println();
            System.out.println("a) add contact");
            System.out.println(COMMAND_BACK);

            command = commandLine.nextCommand();
            switch (command) {
                case "a":
                    addContact(false);
                    contacts = ctx.addresses().getContacts();
                    break;
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        address(contacts.get(index));
                    } catch (NumberFormatException e) {
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equals(command));
    }

    private void addContact(boolean isSubscription) {
        System.out.println();
        System.out.println("Please enter the Bitmessage address you want to add");
        try {
            BitmessageAddress address = new BitmessageAddress(commandLine.nextLineTrimmed());
            System.out.println("Please enter an alias for this address, or an empty string for none");
            String alias = commandLine.nextLineTrimmed();
            if (alias.length() > 0) {
                address.setAlias(alias);
            }
            if (isSubscription) {
                ctx.addSubscribtion(address);
            }
            ctx.addContact(address);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private void subscriptions() {
        String command;
        List<BitmessageAddress> subscriptions = ctx.addresses().getSubscriptions();
        do {
            System.out.println();
            commandLine.listAddresses(subscriptions, "subscriptions");
            System.out.println();
            System.out.println("a) add subscription");
            System.out.println(COMMAND_BACK);

            command = commandLine.nextCommand();
            switch (command) {
                case "a":
                    addContact(true);
                    subscriptions = ctx.addresses().getSubscriptions();
                    break;
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        address(subscriptions.get(index));
                    } catch (NumberFormatException e) {
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equals(command));
    }

    private void address(BitmessageAddress address) {
        System.out.println();
        if (address.getAlias() != null)
            System.out.println(address.getAlias());
        System.out.println(address.getAddress());
        System.out.println("Stream:  " + address.getStream());
        System.out.println("Version: " + address.getVersion());
        if (address.getPrivateKey() == null) {
            if (address.getPubkey() == null) {
                System.out.println("Public key still missing");
            } else {
                System.out.println("Public key available");
            }
        } else {
            if (address.isChan()) {
                System.out.println("Chan");
            } else {
                System.out.println("Identity");
            }
        }
    }

    private void labels() {
        List<Label> labels = ctx.messages().getLabels();
        String command;
        do {
            System.out.println();
            int i = 0;
            for (Label label : labels) {
                i++;
                System.out.print(i + ") " + label);
                int unread = ctx.messages().countUnread(label);
                if (unread > 0) {
                    System.out.println(" [" + unread + "]");
                } else {
                    System.out.println();
                }
            }
            System.out.println("a) Archive");
            System.out.println();
            System.out.println("c) compose message");
            System.out.println("s) compose broadcast");
            System.out.println(COMMAND_BACK);

            command = commandLine.nextCommand();
            switch (command) {
                case "a":
                    messages(null);
                    break;
                case "c":
                    compose(false);
                    break;
                case "s":
                    compose(true);
                    break;
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        messages(labels.get(index));
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equalsIgnoreCase(command));
    }

    private void messages(Label label) {
        String command;
        do {
            List<Plaintext> messages = ctx.messages().findMessages(label);
            System.out.println();
            int i = 0;
            for (Plaintext message : messages) {
                i++;
                System.out.println(i + (message.isUnread() ? ">" : ")") + " From: " + message.getFrom() + "; Subject: " + message.getSubject());
            }
            if (i == 0) {
                System.out.println("There are no messages.");
            }
            System.out.println();
            System.out.println("c) compose message");
            System.out.println("s) compose broadcast");
            if (label.getType() == Label.Type.TRASH) {
                System.out.println("e) empty trash");
            }
            System.out.println(COMMAND_BACK);

            command = commandLine.nextCommand();
            switch (command) {
                case "c":
                    compose(false);
                    break;
                case "s":
                    compose(true);
                    break;
                case "e":
                    messages.forEach(ctx.messages()::remove);
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        show(messages.get(index));
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equalsIgnoreCase(command));
    }

    private void show(Plaintext message) {
        System.out.println();
        System.out.println("From:    " + message.getFrom());
        System.out.println("To:      " + message.getTo());
        System.out.println("Subject: " + message.getSubject());
        System.out.println();
        System.out.println(WordUtils.wrap(message.getText(), 120));
        System.out.println();
        System.out.println(message.getLabels().stream().map(Label::toString).collect(
                Collectors.joining(", ", "Labels: ", "")));
        System.out.println();
        ctx.labeler().markAsRead(message);
        ctx.messages().save(message);
        String command;
        do {
            System.out.println("r) reply");
            System.out.println("d) delete");
            System.out.println("a) archive");
            System.out.println(COMMAND_BACK);
            command = commandLine.nextCommand();
            switch (command) {
                case "r":
                    compose(message.getTo(), message.getFrom(), "RE: " + message.getSubject());
                    break;
                case "d":
                    ctx.labeler().delete(message);
                    ctx.messages().save(message);
                    return;
                case "a":
                    ctx.labeler().archive(message);
                    ctx.messages().save(message);
                    return;
                case "b":
                    return;
                default:
                    System.out.println(ERROR_UNKNOWN_COMMAND);
            }
        } while (!"b".equalsIgnoreCase(command));
    }

    private void compose(boolean broadcast) {
        System.out.println();
        BitmessageAddress from = selectIdentity();
        if (from == null) {
            return;
        }
        BitmessageAddress to = (broadcast ? null : selectContact());
        if (!broadcast && to == null) {
            return;
        }

        compose(from, to, null);
    }

    private BitmessageAddress selectIdentity() {
        List<BitmessageAddress> addresses = ctx.addresses().getIdentities();
        while (addresses.size() == 0) {
            addIdentity();
            addresses = ctx.addresses().getIdentities();
        }
        return commandLine.selectAddress(addresses, "From:");
    }

    private BitmessageAddress selectContact() {
        List<BitmessageAddress> addresses = ctx.addresses().getContacts();
        while (addresses.size() == 0) {
            addContact(false);
            addresses = ctx.addresses().getContacts();
        }
        return commandLine.selectAddress(addresses, "To:");
    }

    private void compose(BitmessageAddress from, BitmessageAddress to, String subject) {
        boolean broadcast = (to == null);
        System.out.println();
        System.out.println("From:    " + from);
        if (!broadcast) {
            System.out.println("To:      " + to);
        }
        if (subject != null) {
            System.out.println("Subject: " + subject);
        } else {
            System.out.print("Subject: ");
            subject = commandLine.nextLineTrimmed();
        }
        System.out.println("Message:");
        StringBuilder message = new StringBuilder();
        String line;
        do {
            line = commandLine.nextLine();
            message.append(line).append('\n');
        } while (line.length() > 0 || !commandLine.yesNo("Send message?"));
        if (broadcast) {
            ctx.broadcast(from, subject, message.toString());
        } else {
            ctx.send(from, to, subject, message.toString());
        }
    }
}
