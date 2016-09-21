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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * The 'verack' command answers a 'version' command, accepting the other node's version.
 */
public class VerAck implements MessagePayload {
    private static final long serialVersionUID = -4302074845199181687L;

    @Override
    public Command getCommand() {
        return Command.VERACK;
    }

    @Override
    public void write(OutputStream stream) throws IOException {
        // 'verack' doesn't have any payload, so there is nothing to write
    }

    @Override
    public void write(ByteBuffer buffer) {
        // 'verack' doesn't have any payload, so there is nothing to write
    }
}
