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

import java.io.*;

import static ch.dissem.bitmessage.utils.Decode.bytes;

/**
 * @author Christian Basler
 */
public class CustomMessage implements MessagePayload {
    private final byte[] data;

    public CustomMessage() {
        this.data = null;
    }

    public CustomMessage(byte[] data) {
        this.data = data;
    }

    public static MessagePayload read(InputStream in, int length) throws IOException {
        return new CustomMessage(bytes(in, length));
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
            out.write(data);
        } else {
            throw new RuntimeException("Tried to write custom message without data. " +
                    "Programmer: did you forget to override #write()?");
        }
    }

    public static CustomMessage error(String message) {
        try {
            return new CustomMessage(("ERROR\n" + message).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
