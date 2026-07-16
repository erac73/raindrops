package io.raindrops.core;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Esquema híbrido AES-256-GCM + Shamir SSS para datos de tamaño arbitrario.
 *
 * <p>Proposición 1 del paper: para D de tamaño arbitrario:
 * <ol>
 *   <li>Generar clave AES k_AES ← {0,1}^256</li>
 *   <li>Cifrar: C = AES-256-GCM(k_AES, D)</li>
 *   <li>Fragmentar con SSS: splits = DROP(k_AES, N, K)</li>
 * </ol>
 *
 * <p>El ciphertext C puede almacenarse en cualquier nodo sin restricciones
 * de privacidad adicionales. Sin k_AES — que vive distribuida en los drops —
 * C es indistinguible de ruido aleatorio (seguridad IND-CCA2).
 *
 * <p>AES-256-GCM provee:
 * <ul>
 *   <li>Confidencialidad (cifrado con clave aleatoria de 256 bits)</li>
 *   <li>Integridad y autenticidad (GCM authentication tag de 128 bits)</li>
 *   <li>No-repudio del ciphertext (tampering es detectable)</li>
 * </ul>
 */
public final class HybridScheme {

    private static final int AES_KEY_BYTES  = 32;   // 256 bits
    private static final int NONCE_BYTES    = 12;   // 96 bits — óptimo para GCM
    private static final int GCM_TAG_BITS   = 128;  // 16 bytes de authentication tag

    private static final SecureRandom RNG = new SecureRandom();

    private HybridScheme() {}

    // ════════════════════════════════════════════════════════════════════
    //  ENCRYPT — Operación de cifrado
    // ════════════════════════════════════════════════════════════════════

    /**
     * Cifra datos arbitrarios con AES-256-GCM y una clave generada aleatoriamente.
     *
     * @param plaintext  Datos a cifrar (cualquier tamaño).
     * @return           {@link EncryptionResult} con clave AES y ciphertext.
     *                   La clave AES debe fragmentarse con SSS inmediatamente.
     */
    public static EncryptionResult encrypt(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("El plaintext no puede ser nulo o vacío.");
        }

        // Generar clave AES aleatoria — este es el secreto S que SSS fragmentará
        byte[] aesKey = new byte[AES_KEY_BYTES];
        RNG.nextBytes(aesKey);

        byte[] ciphertext = aesGcmEncrypt(aesKey, plaintext);

        return new EncryptionResult(aesKey, ciphertext);
    }

    // ════════════════════════════════════════════════════════════════════
    //  DECRYPT — Operación de descifrado
    // ════════════════════════════════════════════════════════════════════

    /**
     * Descifra un ciphertext usando la clave AES reconstruida por SSS.
     *
     * <p>Este método es llamado por el Witness Node en el último paso de
     * RECONSTRUCT, después de haber recombinado la clave AES desde los drops.
     *
     * @param aesKey     La clave AES reconstruida (debe ser exactamente 32 bytes).
     * @param ciphertext El ciphertext producido por {@link #encrypt}.
     * @return           Los datos originales en plaintext.
     * @throws IllegalArgumentException Si el ciphertext está adulterado
     *                                  (GCM authentication tag fallida).
     */
    public static byte[] decrypt(byte[] aesKey, byte[] ciphertext) {
        if (aesKey == null || aesKey.length != AES_KEY_BYTES) {
            throw new IllegalArgumentException(
                "aesKey debe ser exactamente 32 bytes."
            );
        }
        if (ciphertext == null || ciphertext.length <= NONCE_BYTES) {
            throw new IllegalArgumentException("Ciphertext inválido o truncado.");
        }

        return aesGcmDecrypt(aesKey, ciphertext);
    }

    // ════════════════════════════════════════════════════════════════════
    //  AES-256-GCM — Implementación con Bouncy Castle
    // ════════════════════════════════════════════════════════════════════

    /**
     * Cifrado AES-256-GCM.
     *
     * <p>Formato del ciphertext: nonce (12 bytes) ‖ encrypted_data ‖ auth_tag (16 bytes)
     * El nonce se genera aleatoriamente y se prepende al ciphertext.
     * El auth tag es añadido automáticamente por GCM al final.
     */
    private static byte[] aesGcmEncrypt(byte[] key, byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);

        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters params = new AEADParameters(
            new KeyParameter(key), GCM_TAG_BITS, nonce
        );
        cipher.init(true, params); // true = encrypt

        byte[] output = new byte[cipher.getOutputSize(plaintext.length)];
        int offset = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);

        try {
            cipher.doFinal(output, offset);
        } catch (Exception e) {
            throw new CryptoException("Error en cifrado AES-GCM: " + e.getMessage(), e);
        }

        // Prepender el nonce: [nonce | ciphertext+tag]
        byte[] result = new byte[NONCE_BYTES + output.length];
        System.arraycopy(nonce,  0, result, 0,           NONCE_BYTES);
        System.arraycopy(output, 0, result, NONCE_BYTES, output.length);
        return result;
    }

    /**
     * Descifrado AES-256-GCM.
     *
     * <p>Si el authentication tag no coincide (ciphertext adulterado),
     * Bouncy Castle lanza una excepción — esto es la verificación de integridad.
     */
    private static byte[] aesGcmDecrypt(byte[] key, byte[] ciphertextWithNonce) {
        // Separar nonce del ciphertext real
        byte[] nonce      = Arrays.copyOfRange(ciphertextWithNonce, 0, NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(ciphertextWithNonce, NONCE_BYTES,
                                               ciphertextWithNonce.length);

        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters params = new AEADParameters(
            new KeyParameter(key), GCM_TAG_BITS, nonce
        );
        cipher.init(false, params); // false = decrypt

        byte[] output = new byte[cipher.getOutputSize(ciphertext.length)];
        int offset = cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);

        try {
            cipher.doFinal(output, offset);
        } catch (Exception e) {
            // GCM authentication tag inválido — ciphertext adulterado o clave errónea
            throw new CryptoException(
                "Descifrado fallido: authentication tag inválido. " +
                "El ciphertext puede estar adulterado o la clave es incorrecta.", e
            );
        }

        return output;
    }

    // ════════════════════════════════════════════════════════════════════
    //  TIPOS DE RETORNO
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resultado del cifrado: clave AES y ciphertext.
     *
     * <p>IMPORTANTE: la clave AES debe fragmentarse con SSS INMEDIATAMENTE
     * y luego limpiarse de memoria. No debe persistirse sin fragmentar.
     */
    public static final class EncryptionResult implements AutoCloseable {

        private final byte[] aesKey;      // 32 bytes — FRAGMENTAR CON SSS
        private final byte[] ciphertext;  // nonce + encrypted + tag — almacenar

        private EncryptionResult(byte[] aesKey, byte[] ciphertext) {
            this.aesKey     = aesKey;
            this.ciphertext = ciphertext;
        }

        /** La clave AES a fragmentar con SSS. */
        public byte[] getAesKey() { return aesKey.clone(); }

        /** El ciphertext a almacenar (sin restricciones de privacidad). */
        public byte[] getCiphertext() { return ciphertext.clone(); }

        /** Longitud del plaintext original en bytes. */
        public int getCiphertextLength() { return ciphertext.length; }

        /**
         * Limpia la clave AES de memoria.
         * Llamar siempre después de haber fragmentado la clave con SSS.
         */
        @Override
        public void close() {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    // ── Excepción tipada ────────────────────────────────────────────────

    /**
     * Excepcion lanzada cuando ocurre un fallo en las operaciones criptograficas
     * AES-256-GCM (cifrado o descifrado).
     */
    public static class CryptoException extends RuntimeException {
        /**
         * Crea una nueva CryptoException con mensaje y causa original.
         *
         * @param message  Descripcion del fallo criptografico.
         * @param cause    Excepcion original que provoco el fallo.
         */
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
