package io.raindrops.core;

import org.bouncycastle.crypto.SecureRandomProvider;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Shamir's Secret Sharing sobre el campo finito GF(p).
 *
 * <p>Implementa el esquema (K, N)-umbral descrito en el artículo Rain Drops:
 * - {@code split()}   →  Operación DROP: fragmenta S en N shares
 * - {@code combine()} →  Operación RECONSTRUCT: interpola S desde K shares
 *
 * <p>Seguridad: con menos de K shares, la distribución de S es uniforme
 * sobre GF(p). Seguridad perfecta (Teorema 1 del paper).
 *
 * <p>Primo de Mersenne usado: p = 2^521 − 1 (521 bits, NIST P-521 field prime).
 * Cualquier secreto S debe satisfacer 0 ≤ S < p.
 */
public final class ShamirSSS {

    // ── Primo de Mersenne de 521 bits ────────────────────────────────────
    // p = 2^521 - 1  →  el mayor primo de Mersenne conocido con < 600 bits.
    // Elegido porque:
    //   1. Las reducciones mod p son muy eficientes (estructura de Mersenne).
    //   2. 521 bits cubren holgadamente claves AES-256 (256 bits).
    //   3. Es el campo primo de la curva NIST P-521.
    public static final BigInteger PRIME = BigInteger.TWO
            .pow(521)
            .subtract(BigInteger.ONE);

    private static final SecureRandom RNG = new SecureRandom();

    // Constructor privado — clase utilitaria
    private ShamirSSS() {}

    // ════════════════════════════════════════════════════════════════════
    //  SPLIT — Operación DROP
    // ════════════════════════════════════════════════════════════════════

    /**
     * Fragmenta un secreto en {@code n} shares, de los cuales se necesitan
     * exactamente {@code k} para reconstruirlo.
     *
     * <p>Algoritmo:
     * <ol>
     *   <li>Construir un polinomio aleatorio f(x) de grado k-1 sobre GF(p)
     *       donde f(0) = secret.</li>
     *   <li>Evaluar f en los puntos x = 1, 2, …, n.</li>
     *   <li>Cada share es el par (x_i, f(x_i)).</li>
     * </ol>
     *
     * @param secret  El secreto S ∈ [0, PRIME). Debe ser {@code 0 ≤ S < p}.
     * @param n       Número total de shares a generar (N en el paper).
     * @param k       Umbral de reconstrucción — mínimo de shares necesarios.
     * @return        Lista de N shares, cada uno como {@code BigInteger[]{x, y}}.
     * @throws IllegalArgumentException si los parámetros no son válidos.
     */
    public static List<BigInteger[]> split(BigInteger secret, int n, int k) {
        validateParams(secret, n, k);

        // Paso 1 — Construir polinomio f(x) = secret + a1*x + ... + a(k-1)*x^(k-1)
        // Los coeficientes a1..a(k-1) son uniformemente aleatorios en GF(p).
        BigInteger[] coefficients = new BigInteger[k];
        coefficients[0] = secret;                        // término independiente = S
        for (int i = 1; i < k; i++) {
            coefficients[i] = randomFieldElement();      // coeficiente aleatorio
        }

        // Paso 2 — Evaluar f en x = 1, 2, …, n
        List<BigInteger[]> shares = new ArrayList<>(n);
        for (int x = 1; x <= n; x++) {
            BigInteger xVal = BigInteger.valueOf(x);
            BigInteger yVal = evaluatePolynomial(coefficients, xVal);
            shares.add(new BigInteger[]{xVal, yVal});
        }

        // Seguridad: limpiar coeficientes de memoria
        clearArray(coefficients);

        return shares;
    }

    // ════════════════════════════════════════════════════════════════════
    //  COMBINE — Operación RECONSTRUCT (interpolación de Lagrange)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Reconstruye el secreto original desde al menos K shares usando
     * interpolación de Lagrange sobre GF(p).
     *
     * <p>Fórmula: S = f(0) = Σᵢ yᵢ · Lᵢ(0)  mod p
     * donde Lᵢ(0) = ∏ⱼ≠ᵢ (0 - xⱼ) / (xᵢ - xⱼ)  mod p
     *
     * <p>Con exactamente K shares válidos, el resultado es S con certeza.
     * Con menos de K shares, el resultado es matemáticamente inútil
     * (distribución uniforme sobre GF(p)).
     *
     * @param shares  Lista de al menos K shares como {@code BigInteger[]{x, y}}.
     * @return        El secreto reconstruido S.
     */
    public static BigInteger combine(List<BigInteger[]> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un share.");
        }

        BigInteger secret = BigInteger.ZERO;

        for (int i = 0; i < shares.size(); i++) {
            BigInteger xi = shares.get(i)[0];
            BigInteger yi = shares.get(i)[1];

            // Calcular coeficiente de Lagrange L_i(0)
            BigInteger numerator   = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < shares.size(); j++) {
                if (i == j) continue;
                BigInteger xj = shares.get(j)[0];

                // numerator   *= (0 - xj) = -xj
                numerator   = numerator.multiply(xj.negate()).mod(PRIME);
                // denominator *= (xi - xj)
                denominator = denominator.multiply(xi.subtract(xj)).mod(PRIME);
            }

            // Inverso modular del denominador: denominator^(-1) mod p
            BigInteger lagrangeCoeff = numerator
                    .multiply(denominator.modInverse(PRIME))
                    .mod(PRIME);

            // Acumular: secret += yi * L_i(0)
            secret = secret
                    .add(yi.multiply(lagrangeCoeff))
                    .mod(PRIME);
        }

        return secret;
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONVERSIÓN bytes ↔ BigInteger
    // ════════════════════════════════════════════════════════════════════

    /**
     * Convierte bytes a BigInteger para uso como secreto.
     * Usa signum positivo (sin byte de signo) para máxima capacidad.
     *
     * @param bytes  Array de bytes del secreto (ej. clave AES de 32 bytes).
     * @return       BigInteger positivo representando los bytes.
     * @throws IllegalArgumentException si el valor excede PRIME.
     */
    public static BigInteger bytesToSecret(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes); // signum=1: siempre positivo
        if (value.compareTo(PRIME) >= 0) {
            throw new IllegalArgumentException(
                "El secreto (" + bytes.length * 8 + " bits) excede el campo GF(p). " +
                "Usar esquema híbrido para datos mayores de 65 bytes."
            );
        }
        return value;
    }

    /**
     * Convierte un BigInteger de vuelta a bytes de longitud fija.
     *
     * @param value   El BigInteger a convertir.
     * @param length  Longitud deseada en bytes (ej. 32 para AES-256).
     * @return        Array de exactamente {@code length} bytes (padding con ceros).
     */
    public static byte[] secretToBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();

        // BigInteger.toByteArray() puede añadir un byte 0x00 al inicio (signo)
        // o devolver menos bytes si hay ceros al inicio del valor.
        byte[] result = new byte[length];

        if (raw.length <= length) {
            // Copiar al final del array (big-endian, padding izquierdo con 0)
            System.arraycopy(raw, 0, result, length - raw.length, raw.length);
        } else {
            // raw tiene un byte extra de signo al inicio — omitirlo
            System.arraycopy(raw, raw.length - length, result, 0, length);
        }

        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Evalúa el polinomio en x usando el esquema de Horner.
     * Más eficiente que la evaluación directa: O(k) multiplicaciones.
     *
     * f(x) = c[0] + c[1]*x + c[2]*x^2 + ... + c[k-1]*x^(k-1)
     * Horner: f(x) = c[0] + x*(c[1] + x*(c[2] + ... + x*c[k-1]))
     */
    private static BigInteger evaluatePolynomial(BigInteger[] coefficients, BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        // Iterar desde el coeficiente de mayor grado hacia el menor
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients[i]).mod(PRIME);
        }
        return result;
    }

    /**
     * Genera un elemento aleatorio uniforme de GF(p).
     * Rechaza valores >= PRIME para garantizar distribución uniforme.
     */
    private static BigInteger randomFieldElement() {
        BigInteger value;
        do {
            // Generar 521 bits aleatorios seguros
            value = new BigInteger(521, RNG);
        } while (value.compareTo(PRIME) >= 0);
        return value;
    }

    private static void validateParams(BigInteger secret, int n, int k) {
        if (secret == null || secret.signum() < 0 || secret.compareTo(PRIME) >= 0) {
            throw new IllegalArgumentException("Secreto fuera del rango [0, PRIME).");
        }
        if (k < 2) {
            throw new IllegalArgumentException("K debe ser al menos 2.");
        }
        if (n < k) {
            throw new IllegalArgumentException("N debe ser >= K.");
        }
        if (n > 255) {
            throw new IllegalArgumentException("N no debe superar 255 shares.");
        }
    }

    /** Sobreescribe un array de BigInteger con cero (limpieza de memoria). */
    private static void clearArray(BigInteger[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = BigInteger.ZERO;
        }
    }
}
