package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.entity.ObjectMessage;

/**
 * Objects that proof of work is currently being done for.
 *
 * @author Christian Basler
 */
public interface ProofOfWorkRepository {
    ObjectMessage getObject(byte[] initialHash);

    void putObject(ObjectMessage object, long nonceTrialsPerByte, long extraBytes);

    void removeObject(ObjectMessage object);
}
