package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.ObjectMessage;

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

    void removeObject(byte[] initialHash);

    class Item {
        public final ObjectMessage object;
        public final long nonceTrialsPerByte;
        public final long extraBytes;

        public Item(ObjectMessage object, long nonceTrialsPerByte, long extraBytes) {
            this.object = object;
            this.nonceTrialsPerByte = nonceTrialsPerByte;
            this.extraBytes = extraBytes;
        }
    }
}
