package ch.dissem.bitmessage.exception;

import ch.dissem.bitmessage.utils.Strings;

import java.io.IOException;
import java.util.Arrays;

public class InsufficientProofOfWorkException extends IOException {
    public InsufficientProofOfWorkException(byte[] target, byte[] hash) {
        super("Insufficient proof of work: " + Strings.hex(target) + " required, " + Strings.hex(Arrays.copyOfRange(hash, 0, 8)) + " achieved.");
    }
}
