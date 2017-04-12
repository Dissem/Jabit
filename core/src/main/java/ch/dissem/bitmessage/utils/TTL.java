package ch.dissem.bitmessage.utils;

import static ch.dissem.bitmessage.utils.UnixTime.DAY;

/**
 * Stores times to live in seconds for different object types. Usually this shouldn't be messed with, but for tests
 * it might be a good idea to reduce it to a minimum, and on mobile clients you might want to optimize it as well.
 *
 * @author Christian Basler
 */
public class TTL {
    private static long msg = 2 * DAY;
    private static long getpubkey = 2 * DAY;
    private static long pubkey = 28 * DAY;

    public static long msg() {
        return msg;
    }

    public static void msg(long msg) {
        TTL.msg = validate(msg);
    }

    public static long getpubkey() {
        return getpubkey;
    }

    public static void getpubkey(long getpubkey) {
        TTL.getpubkey = validate(getpubkey);
    }

    public static long pubkey() {
        return pubkey;
    }

    public static void pubkey(long pubkey) {
        TTL.pubkey = validate(pubkey);
    }

    private static long validate(long ttl) {
        if (ttl < 0 || ttl > 28 * DAY) throw new IllegalArgumentException("TTL must be between 1 and 28 days");
        return ttl;
    }
}
