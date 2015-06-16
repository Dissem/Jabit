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
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Scanner;

/**
 * A simple command line Bitmessage application
 */
public class Application {
    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private final Scanner scanner;

    private BitmessageContext ctx;

    public Application() {
        JdbcConfig jdbcConfig = new JdbcConfig();
        ctx = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository(jdbcConfig))
                .inventory(new JdbcInventory(jdbcConfig))
                .nodeRegistry(new JdbcNodeRegistry(jdbcConfig))
                .messageRepo(new JdbcMessageRepository(jdbcConfig))
                .networkHandler(new NetworkNode())
                .port(48444)
                .streams(1)
                .build();

        ctx.startup(new BitmessageContext.Listener() {
            @Override
            public void receive(Plaintext plaintext) {
                try {
                    System.out.println(new String(plaintext.getMessage(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        });

        scanner = new Scanner(System.in);

        String command;
        do {
            System.out.println();
            System.out.println("available commands:");
            System.out.println("i) identities");
            System.out.println("c) contacts");
            System.out.println("s) subscriptions");
            System.out.println("m) messages");
            System.out.println("?) info");
            System.out.println("e) exit");

            command = nextCommand();
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
                        messages();
                        break;
                    case "?":
                        info();
                        break;
                    case "e":
                        break;
                    default:
                        System.out.println("Unknown command. Please try again.");
                }
            } catch (Exception e) {
                LOG.debug(e.getMessage());
            }
        } while (!"e".equals(command));
        LOG.info("Shutting down client");
        ctx.shutdown();
    }

    private void info() {
        System.out.println();
        System.out.println(ctx.status());
    }

    private String nextCommand() {
        return scanner.nextLine().trim().toLowerCase();
    }

    private void identities() {
        String command;
        List<BitmessageAddress> identities = ctx.addresses().getIdentities();
        do {
            System.out.println();
            int i = 0;
            for (BitmessageAddress identity : identities) {
                i++;
                System.out.print(i + ") ");
                if (identity.getAlias() != null) {
                    System.out.println(identity.getAlias() + " (" + identity.getAddress() + ")");
                } else {
                    System.out.println(identity.getAddress());
                }
            }
            if (i == 0) {
                System.out.println("You have no identities yet.");
            }
            System.out.println("a) create identity");
            System.out.println("b) back");

            command = nextCommand();
            switch (command) {
                case "a":
                    addIdentity();
                    identities = ctx.addresses().getIdentities();
                    break;
                case "b":
                    return;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        address(identities.get(index));
                    } catch (NumberFormatException e) {
                        System.out.println("Unknown command. Please try again.");
                    }
            }
        } while (!"b".equals(command));
    }

    private void addIdentity() {
        System.out.println();
        BitmessageAddress identity = ctx.createIdentity(yesNo("would you like a shorter address? This will take some time to calculate."), Pubkey.Feature.DOES_ACK);
        System.out.println("Please enter an alias for this identity, or an empty string for none");
        String alias = scanner.nextLine().trim();
        if (alias.length() > 0) {
            identity.setAlias(alias);
        }
        ctx.addresses().save(identity);
    }

    private void contacts() {
        String command;
        List<BitmessageAddress> contacts = ctx.addresses().getContacts();
        do {
            System.out.println();
            int i = 0;
            for (BitmessageAddress contact : contacts) {
                i++;
                System.out.print(i + ") ");
                if (contact.getAlias() != null) {
                    System.out.println(contact.getAlias() + " (" + contact.getAddress() + ")");
                } else {
                    System.out.println(contact.getAddress());
                }
            }
            if (i == 0) {
                System.out.println("You have no contacts yet.");
            }
            System.out.println();
            System.out.println("a) add contact");
            System.out.println("b) back");

            command = nextCommand();
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
                        System.out.println("Unknown command. Please try again.");
                    }
            }
        } while (!"b".equals(command));
    }

    private void addContact(boolean isSubscription) {
        System.out.println();
        System.out.println("Please enter the Bitmessage address you want to add");
        try {
            BitmessageAddress address = new BitmessageAddress(scanner.nextLine().trim());
            System.out.println("Please enter an alias for this address, or an empty string for none");
            String alias = scanner.nextLine().trim();
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
            int i = 0;
            for (BitmessageAddress contact : subscriptions) {
                i++;
                System.out.print(i + ") ");
                if (contact.getAlias() != null) {
                    System.out.println(contact.getAlias() + " (" + contact.getAddress() + ")");
                } else {
                    System.out.println(contact.getAddress());
                }
            }
            if (i == 0) {
                System.out.println("You have no subscriptions yet.");
            }
            System.out.println();
            System.out.println("a) add subscription");
            System.out.println("b) back");

            command = nextCommand();
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
                        System.out.println("Unknown command. Please try again.");
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
            if (address.getPubkey() != null) {
                System.out.println("Public key available");
            } else {
                System.out.println("Public key still missing");
            }
        }
    }

    private void messages() {
        String command;
        List<Plaintext> messages = ctx.messages().findMessages(Plaintext.Status.RECEIVED);
        do {
            System.out.println();
            int i = 0;
            for (Plaintext message : messages) {
                i++;
                System.out.println(i + ") From: " + message.getFrom() + "; Subject: " + message.getSubject());
            }
            if (i == 0) {
                System.out.println("You have no messages.");
            }
            System.out.println();
            System.out.println("c) compose message");
            System.out.println("s) compose broadcast");
            System.out.println("b) back");

            command = scanner.nextLine().trim();
            switch (command) {
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
                        show(messages.get(index));
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        System.out.println("Unknown command. Please try again.");
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
        System.out.println(message.getText());
        System.out.println();
        System.out.println("Labels: " + message.getLabels());
        System.out.println();
        String command;
        do {
            System.out.println("r) reply");
            System.out.println("d) delete");
            System.out.println("b) back");
            command = nextCommand();
            switch (command) {
                case "r":
                    compose(message.getTo(), message.getFrom(), "RE: " + message.getSubject());
                    break;
                case "d":
                    ctx.messages().remove(message);
                case "b":
                    return;
                default:
                    System.out.println("Unknown command. Please try again.");
            }
        } while (!"b".equalsIgnoreCase(command));
    }

    private void compose(boolean broadcast) {
        System.out.println();
        BitmessageAddress from = selectAddress(true);
        if (from == null) {
            return;
        }
        BitmessageAddress to = (broadcast ? null : selectAddress(false));
        if (!broadcast && to == null) {
            return;
        }

        compose(from, to, null);
    }

    private BitmessageAddress selectAddress(boolean id) {
        List<BitmessageAddress> addresses = (id ? ctx.addresses().getIdentities() : ctx.addresses().getContacts());
        while (addresses.size() == 0) {
            if (id) {
                addIdentity();
                addresses = ctx.addresses().getIdentities();
            } else {
                addContact(false);
                addresses = ctx.addresses().getContacts();
            }
        }
        if (addresses.size() == 1) {
            return addresses.get(0);
        }

        String command;
        do {
            System.out.println();
            if (id) {
                System.out.println("From:");
            } else {
                System.out.println("To:");
            }

            int i = 0;
            for (BitmessageAddress identity : addresses) {
                i++;
                System.out.print(i + ") ");
                if (identity.getAlias() != null) {
                    System.out.println(identity.getAlias() + " (" + identity.getAddress() + ")");
                } else {
                    System.out.println(identity.getAddress());
                }
            }
            System.out.println("b) back");

            command = nextCommand();
            switch (command) {
                case "b":
                    return null;
                default:
                    try {
                        int index = Integer.parseInt(command) - 1;
                        if (addresses.get(index) != null) {
                            return addresses.get(index);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Unknown command. Please try again.");
                    }
            }
        } while (!"b".equals(command));
        return null;
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
            subject = scanner.nextLine().trim();
        }
        System.out.println("Message:");
        StringBuilder message = new StringBuilder();
        String line;
        do {
            line = scanner.nextLine();
            message.append(line).append('\n');
        } while (line.length() > 0 || !yesNo("Send message?"));
        if (broadcast) {
            ctx.broadcast(from, subject, message.toString());
        } else {
            ctx.send(from, to, subject, message.toString());
        }
    }

    private boolean yesNo(String question) {
        String answer;
        do {
            System.out.println(question + " (y/n)");
            answer = scanner.nextLine();
            if ("y".equalsIgnoreCase(answer)) return true;
            if ("n".equalsIgnoreCase(answer)) return false;
        } while (true);
    }
}
