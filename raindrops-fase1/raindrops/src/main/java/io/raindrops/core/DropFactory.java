package io.raindrops.core;

import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.KeyParameter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * Fábrica de drops: crea y verifica la integridad de los drops.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Generar identificadores opacos: {@code id = HMAC(nonce, masterKey)}</li>
 *   <li>Calcular MACs de integridad: {@code mac = HMAC(x ‖ y ‖ ttl, masterKey)}</li>
 *   <li>Verificar drops antes de usarlos en RECONSTRUCT</li>
 * </ul>
 *
 * <p>El {@code masterKey} es la clave que une todos los drops de una lluvia.
 * Solo el poseedor del Rain Map conoce cómo obtenerla.
 */
public final class DropFactory {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int NONCE_BYTES  = 32;
    private static final int HMAC_BYTES   = 32; // SHA-256 → 256 bits

    private DropFactory() {}

    // ════════════════════════════════════════════════════════════════════
    //  CREACIÓN
    // ════════════════════════════════════════════════════════════════════

    /**
     * Empaqueta un share SSS como un Drop con identidad ciega y MAC de integridad.
     *
     * @param x         Coordenada x del share (índice 1-based).
     * @param y         Coordenada y = f(x) mod p.
     * @param masterKey Clave maestra de la lluvia (32 bytes).
     * @param ttlDays   Días de vida del drop desde ahora.
     * @return          Drop listo para enviar a un Storage Node.
     */
    public static Drop create(int x, BigInteger y, byte[] masterKey, int ttlDays) {
        validateMasterKey(masterKey);

        long ttl = Instant.now()
                          .plus(ttlDays, ChronoUnit.DAYS)
                          .getEpochSecond();

        // id = HMAC-SHA256(nonce, masterKey) — opaco, sin relación visible
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        byte[] id = hmac(nonce, masterKey);

        // mac = HMAC-SHA256(x ‖ y ‖ ttl, masterKey) — integridad
        byte[] mac = computeMac(x, y, ttl, masterKey);

        return new Drop(id, x, y, mac, ttl);
    }

    /**
     * Versión con TTL absoluto (útil para tests y reconstrucción desde BD).
     *
     * @param x         Coordenada x del share (índice 1-based).
     * @param y         Coordenada y = f(x) mod p.
     * @param masterKey Clave maestra de la lluvia (32 bytes).
     * @param ttlEpoch  Instante de expiracion Unix absoluto en segundos.
     * @return          Drop listo para enviar a un Storage Node.
     */
    public static Drop create(int x, BigInteger y, byte[] masterKey, long ttlEpoch) {
        validateMasterKey(masterKey);

        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        byte[] id  = hmac(nonce, masterKey);
        byte[] mac = computeMac(x, y, ttlEpoch, masterKey);

        return new Drop(id, x, y, mac, ttlEpoch);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VERIFICACIÓN
    // ════════════════════════════════════════════════════════════════════

    /**
     * Verifica que un drop es auténtico (MAC correcto) y vigente (no expirado).
     *
     * <p>Esta verificación DEBE hacerse en el Witness Node antes de usar
     * cualquier drop en la interpolación de Lagrange.
     *
     * @param drop      El drop a verificar.
     * @param masterKey La clave maestra de la lluvia.
     * @return          {@code true} si el drop es válido y no ha expirado.
     */
    public static boolean verify(Drop drop, byte[] masterKey) {
        if (drop == null || masterKey == null) return false;

        // 1. Verificar expiración
        if (drop.isExpired()) return false;

        // 2. Verificar MAC — comparación en tiempo constante (anti-timing attack)
        byte[] expectedMac = computeMac(drop.getX(), drop.getY(), drop.getTtl(), masterKey);
        return constantTimeEquals(expectedMac, drop.getMac());
    }

    /**
     * Lanza excepción detallada si el drop no pasa la verificación.
     * Útil en el Witness Node para log de auditoría.
     *
     * @param drop      El drop a verificar.
     * @param masterKey La clave maestra de la lluvia.
     * @throws InvalidDropException si el drop es nulo, expirado o su MAC es incorrecto.
     */
    public static void verifyOrThrow(Drop drop, byte[] masterKey) {
        if (drop == null) {
            throw new InvalidDropException("Drop nulo.");
        }
        if (drop.isExpired()) {
            throw new InvalidDropException(
                "Drop expirado hace " + (-drop.secondsUntilExpiry()) + " segundos."
            );
        }
        byte[] expectedMac = computeMac(drop.getX(), drop.getY(), drop.getTtl(), masterKey);
        if (!constantTimeEquals(expectedMac, drop.getMac())) {
            throw new InvalidDropException(
                "MAC inválido en drop x=" + drop.getX() + ". Posible adulteración."
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Calcula HMAC-SHA256 del payload del drop.
     * Payload: x (4 bytes, big-endian) ‖ y (66 bytes, big-endian) ‖ ttl (8 bytes)
     */
    private static byte[] computeMac(int x, BigInteger y, long ttl, byte[] key) {
        byte[] yBytes = ShamirSSS.secretToBytes(y, 66); // 66 bytes = ceil(521/8)

        ByteBuffer buf = ByteBuffer.allocate(4 + 66 + 8);
        buf.putInt(x);
        buf.put(yBytes);
        buf.putLong(ttl);

        return hmac(buf.array(), key);
    }

    /**
     * HMAC-SHA256 usando Bouncy Castle (sin JCE — evita restricciones de export).
     */
    static byte[] hmac(byte[] data, byte[] key) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] result = new byte[HMAC_BYTES];
        hmac.doFinal(result, 0);
        return result;
    }

    /**
     * Comparación de arrays en tiempo constante.
     * Previene ataques de timing que podrían inferir el MAC correcto.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= (a[i] ^ b[i]);
        }
        return diff == 0;
    }

    private static void validateMasterKey(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException(
                "masterKey debe ser exactamente 32 bytes (256 bits)."
            );
        }
    }

    // ── Excepción tipada ────────────────────────────────────────────────

    /**
     * Excepcion lanzada cuando un drop es invalido (nulo, expirado o MAC incorrecto).
     */
    public static class InvalidDropException extends RuntimeException {
        /**
         * Crea una nueva InvalidDropException con el mensaje dado.
         *
         * @param message  Descripcion del fallo de verificacion del drop.
         */
        public InvalidDropException(String message) {
            super(message);
        }
    }
}
