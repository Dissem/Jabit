package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;

import java.util.List;

/**
 * Objects that proof of work is currently being done for.
 *
 * @author Christian Basler
 */
public interface ProofOfWorkRepository {
    Item getItem(byte[] initialHash);

    List<byte[]> getItems();

    void putObject(ObjectMessage object, long nonceTrialsPerByte, long extraBytes);

    void putObject(Item item);

    void removeObject(byte[] initialHash);

    class Item {
        public final ObjectMessage object;
        public final long nonceTrialsPerByte;
        public final long extraBytes;

        // Needed for ACK POW calculation
        public final Long expirationTime;
        public final Plaintext message;

        public Item(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) {
            this(object, nonceTrialsPerByte, extraBytes, 0, null);
        }

        public Item(ObjectMessage object, long nonceTrialsPerByte, long extraBytes, long expirationTime, Plaintext message) {
            this.object = object;
            this.nonceTrialsPerByte = nonceTrialsPerByte;
            this.extraBytes = extraBytes;
            this.expirationTime = expirationTime;
            this.message = message;
        }
    }
}
