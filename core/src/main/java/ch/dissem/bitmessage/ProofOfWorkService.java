package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.entity.PlaintextHolder;
import ch.dissem.bitmessage.ports.MessageRepository;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository;
import ch.dissem.bitmessage.ports.Cryptography;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static ch.dissem.bitmessage.utils.Singleton.security;

/**
 * @author Christian Basler
 */
public class ProofOfWorkService implements ProofOfWorkEngine.Callback, InternalContext.ContextHolder {
    private final static Logger LOG = LoggerFactory.getLogger(ProofOfWorkService.class);

    private Cryptography cryptography;
    private InternalContext ctx;
    private ProofOfWorkRepository powRepo;
    private MessageRepository messageRepo;

    public void doMissingProofOfWork() {
        List<byte[]> items = powRepo.getItems();
        if (items.isEmpty()) return;

        LOG.info("Doing POW for " + items.size() + " tasks.");
        for (byte[] initialHash : items) {
            ProofOfWorkRepository.Item item = powRepo.getItem(initialHash);
            cryptography.doProofOfWork(item.object, item.nonceTrialsPerByte, item.extraBytes, this);
        }
    }

    public void doProofOfWork(ObjectMessage object) {
        doProofOfWork(null, object);
    }

    public void doProofOfWork(BitmessageAddress recipient, ObjectMessage object) {
        long nonceTrialsPerByte = recipient == null ?
                ctx.getNetworkNonceTrialsPerByte() : recipient.getPubkey().getNonceTrialsPerByte();
        long extraBytes = recipient == null ?
                ctx.getNetworkExtraBytes() : recipient.getPubkey().getExtraBytes();

        powRepo.putObject(object, nonceTrialsPerByte, extraBytes);
        if (object.getPayload() instanceof PlaintextHolder) {
            Plaintext plaintext = ((PlaintextHolder) object.getPayload()).getPlaintext();
            plaintext.setInitialHash(cryptography.getInitialHash(object));
            messageRepo.save(plaintext);
        }
        cryptography.doProofOfWork(object, nonceTrialsPerByte, extraBytes, this);
    }

    @Override
    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
        ObjectMessage object = powRepo.getItem(initialHash).object;
        object.setNonce(nonce);
//        messageCallback.proofOfWorkCompleted(payload);
        Plaintext plaintext = messageRepo.getMessage(initialHash);
        if (plaintext != null) {
            plaintext.setInventoryVector(object.getInventoryVector());
            messageRepo.save(plaintext);
        }
        ctx.getInventory().storeObject(object);
        ctx.getProofOfWorkRepository().removeObject(initialHash);
        ctx.getNetworkHandler().offer(object.getInventoryVector());
//        messageCallback.messageOffered(payload, object.getInventoryVector());
    }

    @Override
    public void setContext(InternalContext ctx) {
        this.ctx = ctx;
        this.cryptography = security();
        this.powRepo = ctx.getProofOfWorkRepository();
        this.messageRepo = ctx.getMessageRepository();
    }
}
