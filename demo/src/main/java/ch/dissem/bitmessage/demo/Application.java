package ch.dissem.bitmessage.demo;

import ch.dissem.bitmessage.BitmessageContext;
import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.networking.NetworkNode;
import ch.dissem.bitmessage.repository.JdbcAddressRepository;
import ch.dissem.bitmessage.repository.JdbcInventory;
import ch.dissem.bitmessage.repository.JdbcMessageRepository;
import ch.dissem.bitmessage.repository.JdbcNodeRegistry;
import ch.dissem.bitmessage.utils.Strings;
import org.flywaydb.core.internal.util.logging.slf4j.Slf4jLog;
import org.flywaydb.core.internal.util.logging.slf4j.Slf4jLogCreator;
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
        ctx = new BitmessageContext.Builder()
                .addressRepo(new JdbcAddressRepository())
                .inventory(new JdbcInventory())
                .nodeRegistry(new JdbcNodeRegistry())
                .networkHandler(new NetworkNode())
                .messageRepo(new JdbcMessageRepository())
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
            System.out.println("m) messages");
            System.out.println("e) Exit");

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
                    case "m":
                        messages();
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
        String alias = nextCommand();
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
                    addContact();
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

    private void addContact() {
        System.out.println();
        System.out.println("Please enter the Bitmessage address you want to add");
        try {
            BitmessageAddress address = new BitmessageAddress(scanner.nextLine().trim());
            System.out.println("Please enter an alias for this address, or an empty string for none");
            String alias = scanner.nextLine().trim();
            if (alias.length() > 0) {
                address.setAlias(alias);
            }
            ctx.addContact(address);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
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
            System.out.println("b) back");

            command = scanner.nextLine().trim();
            switch (command) {
                case "c":
                    compose();
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
        System.out.println("Labels: "+ message.getLabels());
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

    private void compose() {
        System.out.println();
        BitmessageAddress from = selectAddress(true);
        BitmessageAddress to = selectAddress(false);

        compose(from, to, null);
    }

    private BitmessageAddress selectAddress(boolean id) {
        List<BitmessageAddress> identities = (id ? ctx.addresses().getIdentities() : ctx.addresses().getContacts());
        while (identities.size() == 0) {
            addIdentity();
            identities = ctx.addresses().getIdentities();
        }
        if (identities.size() == 1) {
            return identities.get(0);
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
            for (BitmessageAddress identity : identities) {
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
                        if (identities.get(index) != null) {
                            return identities.get(index);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Unknown command. Please try again.");
                    }
            }
        } while (!"b".equals(command));
        return null;
    }

    private void compose(BitmessageAddress from, BitmessageAddress to, String subject) {
        System.out.println();
        System.out.println("From:    " + from);
        System.out.println("To:      " + to);
        if (subject != null) {
            System.out.println("Subject: " + subject);
        } else {
            System.out.print("Subject: ");
            subject = nextCommand();
        }
        System.out.println("Message:");
        StringBuilder message = new StringBuilder();
        String line;
        do {
            line = nextCommand();
            message.append(line).append('\n');
        } while (line.length() > 0 || !yesNo("Send message?"));
        ctx.send(from, to, subject, message.toString());
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
