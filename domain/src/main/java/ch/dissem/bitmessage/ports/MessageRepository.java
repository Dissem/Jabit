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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.Plaintext.Status;
import ch.dissem.bitmessage.entity.valueobject.Label;

import java.util.List;

public interface MessageRepository {
    List<Label> getLabels();

    List<Label> getLabels(Label.Type... types);

    int countUnread(Label label);

    List<Plaintext> findMessages(Label label);

    List<Plaintext> findMessages(Status status);

    List<Plaintext> findMessages(Status status, BitmessageAddress recipient);

    List<Plaintext> findMessages(BitmessageAddress sender);

    void save(Plaintext message);

    void remove(Plaintext message);
}
