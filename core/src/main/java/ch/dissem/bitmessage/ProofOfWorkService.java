package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.*;
import ch.dissem.bitmessage.entity.payload.Msg;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.ports.Cryptography;
import ch.dissem.bitmessage.ports.MessageRepository;
import ch.dissem.bitmessage.ports.ProofOfWorkEngine;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository;
import ch.dissem.bitmessage.ports.ProofOfWorkRepository.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static ch.dissem.bitmessage.InternalContext.NETWORK_EXTRA_BYTES;
import static ch.dissem.bitmessage.InternalContext.NETWORK_NONCE_TRIALS_PER_BYTE;
import static ch.dissem.bitmessage.utils.Singleton.cryptography;

/**
 * @author Christian Basler
 */
public class ProofOfWorkService implements ProofOfWorkEngine.Callback, InternalContext.ContextHolder {
    private final static Logger LOG = LoggerFactory.getLogger(ProofOfWorkService.class);

    private Cryptography cryptography;
    private InternalContext ctx;
    private ProofOfWorkRepository powRepo;
    private MessageRepository messageRepo;

    public void doMissingProofOfWork(long delayInMilliseconds) {
        final List<byte[]> items = powRepo.getItems();
        if (items.isEmpty()) return;

        // Wait for 30 seconds, to let the application start up before putting heavy load on the CPU
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                LOG.info("Doing POW for " + items.size() + " tasks.");
                for (byte[] initialHash : items) {
                    Item item = powRepo.getItem(initialHash);
                    cryptography.doProofOfWork(item.object, item.nonceTrialsPerByte, item.extraBytes,
                        ProofOfWorkService.this);
                }
            }
        }, delayInMilliseconds);
    }

    public void doProofOfWork(ObjectMessage object) {
        doProofOfWork(null, object);
    }

    public void doProofOfWork(BitmessageAddress recipient, ObjectMessage object) {
        Pubkey pubkey = recipient == null ? null : recipient.getPubkey();

        long nonceTrialsPerByte = pubkey == null ? NETWORK_NONCE_TRIALS_PER_BYTE : pubkey.getNonceTrialsPerByte();
        long extraBytes = pubkey == null ? NETWORK_EXTRA_BYTES : pubkey.getExtraBytes();

        powRepo.putObject(object, nonceTrialsPerByte, extraBytes);
        if (object.getPayload() instanceof PlaintextHolder) {
            Plaintext plaintext = ((PlaintextHolder) object.getPayload()).getPlaintext();
            plaintext.setInitialHash(cryptography.getInitialHash(object));
            messageRepo.save(plaintext);
        }
        cryptography.doProofOfWork(object, nonceTrialsPerByte, extraBytes, this);
    }

    public void doProofOfWorkWithAck(Plaintext plaintext, long expirationTime) {
        final ObjectMessage ack = plaintext.getAckMessage();
        messageRepo.save(plaintext);
        Item item = new Item(ack, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES,
            expirationTime, plaintext);
        powRepo.putObject(item);
        cryptography.doProofOfWork(ack, NETWORK_NONCE_TRIALS_PER_BYTE, NETWORK_EXTRA_BYTES, this);
    }

    @Override
    public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
        Item item = powRepo.getItem(initialHash);
        if (item.message == null) {
            ObjectMessage object = powRepo.getItem(initialHash).object;
            object.setNonce(nonce);
            Plaintext plaintext = messageRepo.getMessage(initialHash);
            if (plaintext != null) {
                plaintext.setInventoryVector(object.getInventoryVector());
                plaintext.updateNextTry();
                ctx.getLabeler().markAsSent(plaintext);
                messageRepo.save(plaintext);
            }
            try {
                ctx.getNetworkListener().receive(object);
            } catch (IOException e) {
                LOG.debug(e.getMessage(), e);
            }
            ctx.getInventory().storeObject(object);
            ctx.getNetworkHandler().offer(object.getInventoryVector());
        } else {
            item.message.getAckMessage().setNonce(nonce);
            final ObjectMessage object = new ObjectMessage.Builder()
                .stream(item.message.getStream())
                .expiresTime(item.expirationTime)
                .payload(new Msg(item.message))
                .build();
            if (object.isSigned()) {
                object.sign(item.message.getFrom().getPrivateKey());
            }
            if (object.getPayload() instanceof Encrypted) {
                object.encrypt(item.message.getTo().getPubkey());
            }
            doProofOfWork(item.message.getTo(), object);
        }
        powRepo.removeObject(initialHash);
    }

    @Override
    public void setContext(InternalContext ctx) {
        this.ctx = ctx;
        this.cryptography = cryptography();
        this.powRepo = ctx.getProofOfWorkRepository();
        this.messageRepo = ctx.getMessageRepository();
    }
}
