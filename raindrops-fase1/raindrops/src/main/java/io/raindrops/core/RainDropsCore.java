package io.raindrops.core;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fachada principal de Rain Drops — Fase 1.
 *
 * <p>Orquesta las operaciones DROP y RECONSTRUCT tal como se definen
 * formalmente en el paper:
 *
 * <pre>
 *   DROP(D, N, K, ttlDays) →  (drops[], ciphertext?, masterKey)
 *   RECONSTRUCT(drops[], masterKey, ciphertext?) →  D
 * </pre>
 *
 * <p>Esta clase implementa el núcleo local (sin red).
 * En fases posteriores, DROP enviará los drops a Storage Nodes remotos
 * y RECONSTRUCT los recolectará desde ellos.
 */
public final class RainDropsCore {

    // Umbral en bytes: datos > 65 bytes usan esquema híbrido AES+SSS.
    // 65 bytes = floor(521/8) — capacidad máxima del campo GF(p).
    private static final int DIRECT_MAX_BYTES = 65;

    private static final SecureRandom RNG = new SecureRandom();

    private RainDropsCore() {}

    // ════════════════════════════════════════════════════════════════════
    //  DROP — Operación de fragmentación
    // ════════════════════════════════════════════════════════════════════

    /**
     * Fragmenta datos en N drops, de los cuales K son suficientes para reconstruir.
     *
     * <p>Para datos pequeños (≤ 65 bytes): fragmenta directamente con SSS.
     * Para datos grandes: usa esquema híbrido (cifra con AES, fragmenta la clave).
     *
     * @param data    Los datos a fragmentar.
     * @param n       Número total de drops a generar.
     * @param k       Umbral de reconstrucción.
     * @param ttlDays Días de vida de cada drop.
     * @return        {@link RainResult} con drops y metadata de reconstrucción.
     */
    public static RainResult drop(byte[] data, int n, int k, int ttlDays) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Los datos no pueden ser nulos o vacíos.");
        }
        if (ttlDays < 1) {
            throw new IllegalArgumentException("ttlDays debe ser al menos 1.");
        }

        // Clave maestra: une todos los drops de esta lluvia
        // Solo el poseedor del Rain Map puede saber cómo obtenerla
        byte[] masterKey = new byte[32];
        RNG.nextBytes(masterKey);

        byte[] ciphertext = null;
        byte[] secretBytes;

        if (data.length <= DIRECT_MAX_BYTES) {
            // ── Modo directo: el dato ES el secreto SSS ──────────────
            secretBytes = padToSize(data, DIRECT_MAX_BYTES);

        } else {
            // ── Modo híbrido: cifrar dato, SSS sobre la clave AES ────
            HybridScheme.EncryptionResult encrypted = HybridScheme.encrypt(data);
            ciphertext  = encrypted.getCiphertext();
            secretBytes = encrypted.getAesKey();   // 32 bytes exactos
            encrypted.close();                     // limpiar clave de EncryptionResult
        }

        // Fragmentar secreto con SSS
        BigInteger secret = ShamirSSS.bytesToSecret(secretBytes);
        Arrays.fill(secretBytes, (byte) 0); // limpiar secreto en claro de memoria

        List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

        // Empaquetar cada share como un Drop
        List<Drop> drops = new ArrayList<>(n);
        for (BigInteger[] share : shares) {
            int       xVal = share[0].intValueExact();
            BigInteger yVal = share[1];
            drops.add(DropFactory.create(xVal, yVal, masterKey, ttlDays));
        }

        return new RainResult(drops, masterKey.clone(), ciphertext, n, k,
                              data.length <= DIRECT_MAX_BYTES);
    }

    // ════════════════════════════════════════════════════════════════════
    //  RECONSTRUCT — Operación de reconstrucción
    // ════════════════════════════════════════════════════════════════════

    /**
     * Reconstruye los datos originales desde al menos K drops válidos.
     *
     * <p>En esta implementación de Fase 1, los drops se pasan directamente.
     * En Fase 3 (Witness Node), los drops se recolectarán desde la red.
     *
     * @param drops      Lista de drops (necesita al menos K válidos).
     * @param masterKey  La clave maestra de la lluvia (del Rain Map).
     * @param ciphertext El ciphertext AES si se usó esquema híbrido, o {@code null}.
     * @param k          El umbral K de la lluvia.
     * @param directMode {@code true} si los datos son ≤ 65 bytes (sin AES).
     * @return           Los datos originales.
     */
    public static byte[] reconstruct(List<Drop> drops, byte[] masterKey,
                                     byte[] ciphertext, int k, boolean directMode) {
        if (drops == null || drops.size() < k) {
            throw new QuorumException(
                "Quórum insuficiente: se necesitan " + k +
                " drops, se proporcionaron " + (drops == null ? 0 : drops.size()) + "."
            );
        }

        // 1. Verificar integridad y vigencia de cada drop
        List<BigInteger[]> validShares = new ArrayList<>();
        for (Drop drop : drops) {
            DropFactory.verifyOrThrow(drop, masterKey); // lanza si falla
            validShares.add(new BigInteger[]{
                BigInteger.valueOf(drop.getX()),
                drop.getY()
            });
            if (validShares.size() == k) break; // tenemos suficientes
        }

        // 2. Interpolación de Lagrange — reconstruir secreto
        BigInteger secret = ShamirSSS.combine(validShares);

        // 3. Extraer datos según el modo
        byte[] result;
        if (directMode) {
            // Modo directo: el secreto ES el dato (con padding)
            byte[] padded = ShamirSSS.secretToBytes(secret, DIRECT_MAX_BYTES);
            // Devolver sin el padding de ceros al final
            result = trimTrailingZeros(padded);
        } else {
            // Modo híbrido: el secreto es la clave AES
            if (ciphertext == null) {
                throw new IllegalArgumentException(
                    "Se requiere ciphertext para modo híbrido."
                );
            }
            byte[] aesKey = ShamirSSS.secretToBytes(secret, 32);
            result = HybridScheme.decrypt(aesKey, ciphertext);
            Arrays.fill(aesKey, (byte) 0); // limpiar clave AES reconstruida
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    //  TIPOS DE RETORNO
    // ════════════════════════════════════════════════════════════════════

    /**
     * Resultado de la operación DROP.
     *
     * <p>Contiene todo lo necesario para reconstruir el dato posteriormente:
     * los drops (que van a la red), la masterKey (que va al Rain Map cifrado),
     * y el ciphertext (si se usó modo híbrido).
     */
    public static final class RainResult {

        private final List<Drop> drops;
        private final byte[]     masterKey;   // → Rain Map (cifrar y custodiar)
        private final byte[]     ciphertext;  // → Storage Node (si modo híbrido)
        private final int        n;
        private final int        k;
        private final boolean    directMode;

        private RainResult(List<Drop> drops, byte[] masterKey, byte[] ciphertext,
                           int n, int k, boolean directMode) {
            this.drops      = List.copyOf(drops);
            this.masterKey  = masterKey;
            this.ciphertext = ciphertext;
            this.n          = n;
            this.k          = k;
            this.directMode = directMode;
        }

        public List<Drop> getDrops()      { return drops; }
        public byte[]     getMasterKey()  { return masterKey.clone(); }
        public byte[]     getCiphertext() { return ciphertext != null ? ciphertext.clone() : null; }
        public int        getN()          { return n; }
        public int        getK()          { return k; }
        public boolean    isDirectMode()  { return directMode; }

        @Override
        public String toString() {
            return "RainResult{n=" + n + ", k=" + k +
                   ", mode=" + (directMode ? "direct" : "hybrid") +
                   ", drops=" + drops.size() + "}";
        }
    }

    // ── Excepción de quórum ─────────────────────────────────────────────

    public static class QuorumException extends RuntimeException {
        public QuorumException(String message) {
            super(message);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════

    /** Añade padding de ceros a la derecha hasta alcanzar el tamaño deseado. */
    private static byte[] padToSize(byte[] data, int size) {
        if (data.length == size) return data.clone();
        byte[] padded = new byte[size];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    /**
     * Elimina ceros de relleno al final de un array.
     * Necesario para recuperar el dato original en modo directo.
     *
     * <p>Limitación conocida: si el dato original termina en bytes 0x00,
     * esos bytes se perderán. Para datos que puedan terminar en ceros,
     * usar modo híbrido o añadir longitud como prefijo.
     * Esto es documentado como una limitación del modo directo.
     */
    private static byte[] trimTrailingZeros(byte[] data) {
        int lastNonZero = data.length - 1;
        while (lastNonZero >= 0 && data[lastNonZero] == 0) {
            lastNonZero--;
        }
        return Arrays.copyOf(data, lastNonZero + 1);
    }
}
