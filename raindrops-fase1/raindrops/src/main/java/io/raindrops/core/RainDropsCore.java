package io.raindrops.core;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fachada principal de Rain Drops.
 *
 * <p>Operaciones DROP y RECONSTRUCT:
 * <pre>
 *   DROP(D, N, K, ttlDays) →  (drops[], ciphertext?, masterKey, commitments?)
 *   RECONSTRUCT(drops[], masterKey, ciphertext?, commitments?) →  D
 * </pre>
 *
 * <p>VSS (Feldman): el dealer genera commitments publicos a partir de los
 * coefficients del polinomio SSS. Cada poseedor de share puede verificar
 * su validez matematica sin revelar el secreto.
 */
public final class RainDropsCore {

    private static final int DIRECT_MAX_BYTES = 65;
    private static final SecureRandom RNG = new SecureRandom();

    private RainDropsCore() {}

    // ════════════════════════════════════════════════════════════════════
    //  DROP — SSS + VSS commitments
    // ════════════════════════════════════════════════════════════════════

    public static RainResult drop(byte[] data, int n, int k, int ttlDays) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Los datos no pueden ser nulos o vacios.");
        }
        if (ttlDays < 1) {
            throw new IllegalArgumentException("ttlDays debe ser al menos 1.");
        }

        byte[] masterKey = new byte[32];
        RNG.nextBytes(masterKey);

        byte[] ciphertext = null;
        byte[] secretBytes;

        if (data.length <= DIRECT_MAX_BYTES) {
            secretBytes = padToSize(data, DIRECT_MAX_BYTES);
        } else {
            HybridScheme.EncryptionResult encrypted = HybridScheme.encrypt(data);
            ciphertext  = encrypted.getCiphertext();
            secretBytes = encrypted.getAesKey();
            encrypted.close();
        }

        BigInteger secret = ShamirSSS.bytesToSecret(secretBytes);
        Arrays.fill(secretBytes, (byte) 0);

        // SSS split — produces shares AND coefficients in GF(p)
        Object[] splitResult = ShamirSSS.splitWithCoefficients(secret, n, k);
        @SuppressWarnings("unchecked")
        List<BigInteger[]> shares = (List<BigInteger[]>) splitResult[0];
        BigInteger[] coefficients = (BigInteger[]) splitResult[1];

        // VSS commitments from the SAME polynomial coefficients
        List<BigInteger> commitments = FeldmanVSS.computeCommitments(coefficients);

        // Clean coefficients from memory
        Arrays.fill(coefficients, BigInteger.ZERO);

        List<Drop> drops = new ArrayList<>(n);
        for (BigInteger[] share : shares) {
            int       xVal = share[0].intValueExact();
            BigInteger yVal = share[1];
            drops.add(DropFactory.create(xVal, yVal, masterKey, ttlDays));
        }

        return new RainResult(drops, masterKey.clone(), ciphertext, n, k,
                              data.length <= DIRECT_MAX_BYTES, commitments);
    }

    // ════════════════════════════════════════════════════════════════════
    //  RECONSTRUCT — VSS verification + SSS combine
    // ════════════════════════════════════════════════════════════════════

    public static byte[] reconstruct(List<Drop> drops, byte[] masterKey,
                                     byte[] ciphertext, int k, boolean directMode,
                                     List<BigInteger> commitments) {
        if (drops == null || drops.size() < k) {
            throw new QuorumException(
                "Quórum insuficiente: se necesitan " + k +
                " drops, se proporcionaron " + (drops == null ? 0 : drops.size()) + "."
            );
        }

        // 1. Verify MAC integrity + expiry
        List<BigInteger[]> validShares = new ArrayList<>();
        for (Drop drop : drops) {
            DropFactory.verifyOrThrow(drop, masterKey);

            // 2. Verify VSS — share mathematically consistent with commitments
            if (commitments != null && !commitments.isEmpty()) {
                FeldmanVSS.verifyShareOrThrow(drop.getX(), drop.getY(), commitments);
            }

            validShares.add(new BigInteger[]{
                BigInteger.valueOf(drop.getX()),
                drop.getY()
            });
            if (validShares.size() == k) break;
        }

        // 3. Lagrange interpolation over GF(p) — ShamirSSS combine
        BigInteger secret = ShamirSSS.combine(validShares);

        // 4. Extract data
        byte[] result;
        if (directMode) {
            byte[] padded = ShamirSSS.secretToBytes(secret, DIRECT_MAX_BYTES);
            result = trimTrailingZeros(padded);
        } else {
            if (ciphertext == null) {
                throw new IllegalArgumentException("Se requiere ciphertext para modo hibrido.");
            }
            byte[] aesKey = ShamirSSS.secretToBytes(secret, 32);
            result = HybridScheme.decrypt(aesKey, ciphertext);
            Arrays.fill(aesKey, (byte) 0);
        }

        return result;
    }

    public static byte[] reconstruct(List<Drop> drops, byte[] masterKey,
                                     byte[] ciphertext, int k, boolean directMode) {
        return reconstruct(drops, masterKey, ciphertext, k, directMode, null);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TIPOS DE RETORNO
    // ════════════════════════════════════════════════════════════════════

    public static final class RainResult {
        private final List<Drop> drops;
        private final byte[]     masterKey;
        private final byte[]     ciphertext;
        private final int        n;
        private final int        k;
        private final boolean    directMode;
        private final List<BigInteger> commitments;

        private RainResult(List<Drop> drops, byte[] masterKey, byte[] ciphertext,
                           int n, int k, boolean directMode,
                           List<BigInteger> commitments) {
            this.drops      = List.copyOf(drops);
            this.masterKey  = masterKey;
            this.ciphertext = ciphertext;
            this.n          = n;
            this.k          = k;
            this.directMode = directMode;
            this.commitments = commitments != null ? List.copyOf(commitments) : null;
        }

        public List<Drop> getDrops()              { return drops; }
        public byte[]     getMasterKey()          { return masterKey.clone(); }
        public byte[]     getCiphertext()         { return ciphertext != null ? ciphertext.clone() : null; }
        public int        getN()                  { return n; }
        public int        getK()                  { return k; }
        public boolean    isDirectMode()          { return directMode; }
        public List<BigInteger> getCommitments()  { return commitments; }
        public boolean    hasCommitments()        { return commitments != null && !commitments.isEmpty(); }

        @Override
        public String toString() {
            return "RainResult{n=" + n + ", k=" + k +
                   ", mode=" + (directMode ? "direct" : "hybrid") +
                   ", drops=" + drops.size() +
                   ", vss=" + hasCommitments() + "}";
        }
    }

    public static class QuorumException extends RuntimeException {
        public QuorumException(String message) { super(message); }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private static byte[] padToSize(byte[] data, int size) {
        if (data.length == size) return data.clone();
        byte[] padded = new byte[size];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    private static byte[] trimTrailingZeros(byte[] data) {
        int lastNonZero = data.length - 1;
        while (lastNonZero >= 0 && data[lastNonZero] == 0) {
            lastNonZero--;
        }
        return Arrays.copyOf(data, lastNonZero + 1);
    }
}
