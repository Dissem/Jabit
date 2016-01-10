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

import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.exception.InsufficientProofOfWorkException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Provides some methods to help with hashing and encryption. All randoms are created using {@link SecureRandom},
 * which should be secure enough.
 */
public interface Security {
    /**
     * A helper method to calculate SHA-512 hashes. Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.
     *
     * @param data to get hashed
     * @return SHA-512 hash of data
     */
    byte[] sha512(byte[]... data);

    /**
     * A helper method to calculate doubleSHA-512 hashes. Please note that a new {@link MessageDigest} object is created
     * at each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in
     * success on the same thread.
     *
     * @param data to get hashed
     * @return SHA-512 hash of data
     */
    byte[] doubleSha512(byte[]... data);

    /**
     * A helper method to calculate double SHA-512 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at each call (to ensure thread safety), so you
     * shouldn't use this if you need to do many hash calculations in short order on the same thread.
     * </p>
     *
     * @param data   to get hashed
     * @param length number of bytes to be taken into account
     * @return SHA-512 hash of data
     */
    byte[] doubleSha512(byte[] data, int length);

    /**
     * A helper method to calculate RIPEMD-160 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data to get hashed
     * @return RIPEMD-160 hash of data
     */
    byte[] ripemd160(byte[]... data);

    /**
     * A helper method to calculate double SHA-256 hashes. This method allows to only use a part of the available bytes
     * to use for the hash calculation.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data   to get hashed
     * @param length number of bytes to be taken into account
     * @return SHA-256 hash of data
     */
    byte[] doubleSha256(byte[] data, int length);

    /**
     * A helper method to calculate SHA-1 hashes. Supplying multiple byte arrays has the same result as a
     * concatenation of all arrays, but might perform better.
     * <p>
     * Please note that a new {@link MessageDigest} object is created at
     * each call (to ensure thread safety), so you shouldn't use this if you need to do many hash calculations in short
     * order on the same thread.
     * </p>
     *
     * @param data to get hashed
     * @return SHA hash of data
     */
    byte[] sha1(byte[]... data);

    /**
     * @param length number of bytes to return
     * @return an array of the given size containing random bytes
     */
    byte[] randomBytes(int length);

    /**
     * Calculates the proof of work. This might take a long time, depending on the hardware, message size and time to
     * live.
     *
     * @param object             to do the proof of work for
     * @param nonceTrialsPerByte difficulty
     * @param extraBytes         bytes to add to the object size (makes it more difficult to send small messages)
     * @param callback           to handle nonce once it's calculated
     */
    void doProofOfWork(ObjectMessage object, long nonceTrialsPerByte,
                       long extraBytes, ProofOfWorkEngine.Callback callback);

    /**
     * @param object             to be checked
     * @param nonceTrialsPerByte difficulty
     * @param extraBytes         bytes to add to the object size
     * @throws InsufficientProofOfWorkException if proof of work doesn't check out (makes it more difficult to send small messages)
     */
    void checkProofOfWork(ObjectMessage object, long nonceTrialsPerByte, long extraBytes)
            throws IOException;

    byte[] getInitialHash(ObjectMessage object);

    byte[] getProofOfWorkTarget(ObjectMessage object, long nonceTrialsPerByte, long extraBytes);

    /**
     * Calculates the MAC for a message (data)
     *
     * @param key_m the symmetric key used
     * @param data  the message data to calculate the MAC for
     * @return the MAC
     */
    byte[] mac(byte[] key_m, byte[] data);

    /**
     * @param encrypt if true, encrypts data, otherwise tries to decrypt it.
     * @param data
     * @param key_e
     * @return
     */
    byte[] crypt(boolean encrypt, byte[] data, byte[] key_e, byte[] initializationVector);

    /**
     * Create a new public key fom given private keys.
     *
     * @param version              of the public key / address
     * @param stream               of the address
     * @param privateSigningKey    private key used for signing
     * @param privateEncryptionKey private key used for encryption
     * @param nonceTrialsPerByte   proof of work difficulty
     * @param extraBytes           bytes to add for the proof of work (make it harder for small messages)
     * @param features             of the address
     * @return a public key object
     */
    Pubkey createPubkey(long version, long stream, byte[] privateSigningKey, byte[] privateEncryptionKey,
                        long nonceTrialsPerByte, long extraBytes, Pubkey.Feature... features);

    /**
     * @param privateKey private key as byte array
     * @return a public key corresponding to the given private key
     */
    byte[] createPublicKey(byte[] privateKey);

    /**
     * @param privateKey private key as byte array
     * @return a big integer representation (unsigned) of the given bytes
     */
    BigInteger keyToBigInt(byte[] privateKey);

    /**
     * @param data      to check
     * @param signature the signature of the message
     * @param pubkey    the sender's public key
     * @return true if the signature is valid, false otherwise
     */
    boolean isSignatureValid(byte[] data, byte[] signature, Pubkey pubkey);

    /**
     * Calculate the signature of data, using the given private key.
     *
     * @param data       to be signed
     * @param privateKey to be used for signing
     * @return the signature
     */
    byte[] getSignature(byte[] data, ch.dissem.bitmessage.entity.valueobject.PrivateKey privateKey);

    /**
     * @return a random number of type long
     */
    long randomNonce();

    byte[] multiply(byte[] k, byte[] r);

    byte[] createPoint(byte[] x, byte[] y);
}
