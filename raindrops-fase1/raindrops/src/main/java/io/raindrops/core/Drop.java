package io.raindrops.core;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Representa un drop: la unidad mínima de Rain Drops.
 *
 * <p>Definición formal (Definition 1 del paper):
 * {@code d = (id, x, y, mac, ttl)}
 * <ul>
 *   <li>{@code id}  — identificador opaco: HMAC(nonce, masterKey). Sin relación
 *                     visible con el dato original ni con otros drops.</li>
 *   <li>{@code x}   — coordenada x del punto SSS (índice del share: 1..N).</li>
 *   <li>{@code y}   — coordenada y: f(x) mod p, el share real.</li>
 *   <li>{@code mac} — HMAC-SHA256(x ‖ y ‖ ttl, masterKey). Garantiza integridad.</li>
 *   <li>{@code ttl} — instante de expiración Unix. Drops expirados son irrecuperables.</li>
 * </ul>
 *
 * <p>Un drop en aislamiento es indistinguible de ruido aleatorio.
 * Su significado emerge solo cuando K drops se combinan en RECONSTRUCT.
 */
public final class Drop {

    private final byte[]     id;      // 32 bytes — identificador opaco
    private final int        x;       // coordenada x (1-based index)
    private final BigInteger y;       // coordenada y = f(x) mod p
    private final byte[]     mac;     // 32 bytes — HMAC-SHA256 de integridad
    private final long       ttl;     // Unix epoch seconds de expiración

    /**
     * Constructor publico de Drop.
     *
     * @param id   Identificador opaco de 32 bytes (HMAC-SHA256).
     * @param x    Coordenada x del share SSS (indice 1-based).
     * @param y    Coordenada y del share = f(x) mod p.
     * @param mac  HMAC-SHA256 de integridad de 32 bytes.
     * @param ttl  Instante de expiracion Unix en segundos.
     * @throws NullPointerException si id, y o mac son nulos.
     */
    public Drop(byte[] id, int x, BigInteger y, byte[] mac, long ttl) {
        this.id  = Objects.requireNonNull(id,  "id no puede ser null").clone();
        this.x   = x;
        this.y   = Objects.requireNonNull(y,   "y no puede ser null");
        this.mac = Objects.requireNonNull(mac, "mac no puede ser null").clone();
        this.ttl = ttl;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    /** @return Clon del identificador opaco de 32 bytes. */
    public byte[]     getId()  { return id.clone(); }

    /** @return Coordenada x del share (indice 1-based). */
    public int        getX()   { return x; }

    /** @return Coordenada y del share = f(x) mod p. */
    public BigInteger getY()   { return y; }

    /** @return Clon del HMAC-SHA256 de integridad de 32 bytes. */
    public byte[]     getMac() { return mac.clone(); }

    /** @return Instante de expiracion Unix en segundos. */
    public long       getTtl() { return ttl; }

    // ── Estado temporal ──────────────────────────────────────────────────

    /**
     * @return {@code true} si el drop ha expirado en el momento actual.
     */
    public boolean isExpired() {
        return Instant.now().getEpochSecond() > ttl;
    }

    /**
     * @return Segundos restantes hasta la expiración (negativo si ya expiró).
     */
    public long secondsUntilExpiry() {
        return ttl - Instant.now().getEpochSecond();
    }

    // ── Representación ───────────────────────────────────────────────────

    @Override
    public String toString() {
        HexFormat hex = HexFormat.of();
        return "Drop{" +
               "id="  + hex.formatHex(id, 0, 8) + "..." +
               ", x=" + x +
               ", ttl=" + Instant.ofEpochSecond(ttl) +
               ", expired=" + isExpired() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Drop d)) return false;
        return x == d.x && ttl == d.ttl
               && Arrays.equals(id, d.id)
               && y.equals(d.y)
               && Arrays.equals(mac, d.mac);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(x, y, ttl);
        result = 31 * result + Arrays.hashCode(id);
        result = 31 * result + Arrays.hashCode(mac);
        return result;
    }
}
