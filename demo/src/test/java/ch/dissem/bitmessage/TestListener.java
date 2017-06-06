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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.Plaintext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by chrig on 02.02.2016.
 */
public class TestListener implements BitmessageContext.Listener {
    private CompletableFuture<Plaintext> future = new CompletableFuture<>();

    @Override
    public void receive(Plaintext plaintext) {
        future.complete(plaintext);
    }

    public void reset() {
        future = new CompletableFuture<>();
    }

    public Plaintext get(long timeout, TimeUnit unit) throws Exception {
        return future.get(timeout, unit);
    }
}
