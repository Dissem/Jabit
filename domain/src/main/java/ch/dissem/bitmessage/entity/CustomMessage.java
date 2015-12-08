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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.utils.AccessCounter;
import ch.dissem.bitmessage.utils.Encode;

import java.io.*;

import static ch.dissem.bitmessage.utils.Decode.bytes;
import static ch.dissem.bitmessage.utils.Decode.varString;

/**
 * @author Christian Basler
 */
public class CustomMessage implements MessagePayload {
    public static final String COMMAND_ERROR = "ERROR";

    private final String command;
    private final byte[] data;

    public CustomMessage(String command) {
        this.command = command;
        this.data = null;
    }

    public CustomMessage(String command, byte[] data) {
        this.command = command;
        this.data = data;
    }

    public static MessagePayload read(InputStream in, int length) throws IOException {
        AccessCounter counter = new AccessCounter();
        return new CustomMessage(varString(in, counter), bytes(in, length - counter.length()));
    }

    @Override
    public Command getCommand() {
        return Command.CUSTOM;
    }

    public byte[] getData() throws IOException {
        if (data != null) {
            return data;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out);
            return out.toByteArray();
        }
    }

    @Override
    public void write(OutputStream out) throws IOException {
        if (data != null) {
            Encode.varString(command, out);
            out.write(data);
        } else {
            throw new RuntimeException("Tried to write custom message without data. " +
                    "Programmer: did you forget to override #write()?");
        }
    }

    public boolean isError() {
        return COMMAND_ERROR.equals(command);
    }

    public static CustomMessage error(String message) {
        try {
            return new CustomMessage(COMMAND_ERROR, message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
