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

package ch.dissem.bitmessage.demo;

import ch.dissem.bitmessage.entity.BitmessageAddress;

import java.util.List;
import java.util.Scanner;


/**
 * @author Christian Basler
 */
public class CommandLine {
    public static final String COMMAND_BACK = "b) back";
    public static final String ERROR_UNKNOWN_COMMAND = "Unknown command. Please try again.";

    private Scanner scanner = new Scanner(System.in);

    public String nextCommand() {
        return scanner.nextLine().trim().toLowerCase();
    }

    public String nextLine() {
        return scanner.nextLine();
    }

    public String nextLineTrimmed() {
        return scanner.nextLine();
    }

    public boolean yesNo(String question) {
        String answer;
        do {
            System.out.println(question + " (y/n)");
            answer = scanner.nextLine();
            if ("y".equalsIgnoreCase(answer)) return true;
            if ("n".equalsIgnoreCase(answer)) return false;
        } while (true);
    }

    public BitmessageAddress selectAddress(List<BitmessageAddress> addresses, String label) {
        if (addresses.size() == 1) {
            return addresses.get(0);
        }

        String command;
        do {
            System.out.println();
            System.out.println(label);

            listAddresses(addresses, "contacts");
            System.out.println(COMMAND_BACK);

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
                        System.out.println(ERROR_UNKNOWN_COMMAND);
                    }
            }
        } while (!"b".equals(command));
        return null;
    }

    public void listAddresses(List<BitmessageAddress> addresses, String kind) {
        int i = 0;
        for (BitmessageAddress address : addresses) {
            i++;
            System.out.print(i + ") ");
            if (address.getAlias() == null) {
                System.out.println(address.getAddress());
            } else {
                System.out.println(address.getAlias() + " (" + address.getAddress() + ")");
            }
        }
        if (i == 0) {
            System.out.println("You have no " + kind + " yet.");
        }
    }
}
