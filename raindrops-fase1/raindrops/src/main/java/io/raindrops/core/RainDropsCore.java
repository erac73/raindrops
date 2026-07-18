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

    /**
     * Divide los datos en N drops utilizando Shamir Secret Sharing con
     * verificabilidad de Feldman VSS.
     *
     * <p>Si los datos superan 65 bytes, se cifran con AES-256-GCM (modo hibrido)
     * y solo la clave AES se fragmenta via SSS. Si no, los datos se usan
     * directamente como secreto (modo directo).</p>
     *
     * @param data     datos originales a proteger (no nulos ni vacios).
     * @param n        numero total de shares a generar (debe ser >= k y <= 255).
     * @param k        umbral minimo de shares para reconstruir (debe ser >= 2).
     * @param ttlDays  tiempo de vida de los drops en dias (debe ser >= 1).
     * @return resultado que contiene los drops generados, la clave maestra,
     *         el ciphertext (si aplica), los parametros n/k y los commitments VSS.
     * @throws IllegalArgumentException si data es nula/vacia, ttlDays < 1,
     *         o n/k fuera de rango.
     */
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

    /**
     * Reconstruye los datos originales a partir de drops validados con VSS.
     *
     * <p>Verifica la integridad HMAC de cada drop, comprueba la consistencia
     * matematica con los commitments de Feldman VSS (si se proporcionan),
     * y finalmente interpola el secreto via Lagrange sobre GF(p).</p>
     *
     * @param drops       lista de drops a utilizar para la reconstruccion (debe contener >= k drops validos).
     * @param masterKey   clave maestra para verificar la integridad HMAC de cada drop.
     * @param ciphertext  cifrado adicional para modo hibrido (null si es modo directo).
     * @param k           umbral minimo de shares requeridos.
     * @param directMode  {@code true} si los datos se usaron directamente sin cifrado hibrido.
     * @param commitments lista de commitments de Feldman VSS para verificacion (puede ser null).
     * @return datos originales reconstruidos.
     * @throws QuorumException     si hay menos de k drops validos.
     * @throws IllegalArgumentException si falta ciphertext en modo hibrido.
     */
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

    /**
     * Reconstruye los datos originales sin verificacion VSS (compatibilidad).
     *
     * <p>Equivalente a llamar a {@link #reconstruct(List, byte[], byte[], int, boolean, List)}
     * con {@code commitments = null}.</p>
     *
     * @param drops       lista de drops a utilizar para la reconstruccion.
     * @param masterKey   clave maestra para verificar la integridad HMAC.
     * @param ciphertext  cifrado adicional para modo hibrido (null si es directo).
     * @param k           umbral minimo de shares requeridos.
     * @param directMode  {@code true} si los datos se usaron directamente.
     * @return datos originales reconstruidos.
     * @throws QuorumException     si hay menos de k drops validos.
     */
    public static byte[] reconstruct(List<Drop> drops, byte[] masterKey,
                                     byte[] ciphertext, int k, boolean directMode) {
        return reconstruct(drops, masterKey, ciphertext, k, directMode, null);
    }

    // ════════════════════════════════════════════════════════════════════
    //  TIPOS DE RETORNO
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resultado de la operacion DROP que contiene los fragments generados
     * y toda la informacion necesaria para la reconstruccion.
     *
     * <p>Inmutabilidad: todos los campos se copian defensivamente en el constructor
     * y los getters de arrays/clones retornan copias para prevenir mutaciones externas.</p>
     */
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

        /** @return lista inmutable de drops generados. */
        public List<Drop> getDrops()              { return drops; }
        /** @return clone de la clave maestra de 32 bytes. */
        public byte[]     getMasterKey()          { return masterKey.clone(); }
        /** @return clone del ciphertext (null si modo directo). */
        public byte[]     getCiphertext()         { return ciphertext != null ? ciphertext.clone() : null; }
        /** @return numero total de shares generados. */
        public int        getN()                  { return n; }
        /** @return umbral minimo de shares para reconstruccion. */
        public int        getK()                  { return k; }
        /** @return {@code true} si se usaron datos directos sin cifrado hibrido. */
        public boolean    isDirectMode()          { return directMode; }
        /** @return lista inmutable de commitments de Feldman VSS, o null si no aplica. */
        public List<BigInteger> getCommitments()  { return commitments; }
        /** @return {@code true} si la lista de commitments no es nula ni esta vacia. */
        public boolean    hasCommitments()        { return commitments != null && !commitments.isEmpty(); }

        @Override
        public String toString() {
            return "RainResult{n=" + n + ", k=" + k +
                   ", mode=" + (directMode ? "direct" : "hybrid") +
                   ", drops=" + drops.size() +
                   ", vss=" + hasCommitments() + "}";
        }
    }

    /**
     * Excepcion lanzada cuando no se dispone del quorum minimo de drops
     * validos para reconstruir el secreto.
     *
     * <p>Extiende {@link RuntimeException} para que no sea obligatorio
     * capturarla en cada punto de llamada.</p>
     */
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
