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
