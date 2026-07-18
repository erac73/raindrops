package io.raindrops.core;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Shamir's Secret Sharing sobre el campo finito GF(p).
 *
 * <p>Implementa el esquema (K, N)-umbral descrito en el paper Rain Drops:
 * - {@code split()}   ->  Operacion DROP: fragmenta S en N shares
 * - {@code combine()} ->  Operacion RECONSTRUCT: interpola S desde K shares
 *
 * <p>Seguridad: con menos de K shares, la distribucion de S es uniforme
 * sobre GF(p). Seguridad perfecta (Teorema 1 del paper).
 *
 * <p>Primo de Mersenne usado: p = 2^521 - 1 (521 bits, NIST P-521 field prime).
 * Cualquier secreto S debe satisfacer 0 <= S < p.
 */
public final class ShamirSSS {

    // -- Primo de Mersenne de 521 bits ----------------------------------
    public static final BigInteger PRIME = BigInteger.TWO
            .pow(521)
            .subtract(BigInteger.ONE);

    private static final SecureRandom RNG = new SecureRandom();

    private ShamirSSS() {}

    // =================================================================
    //  SPLIT -- Operacion DROP
    // =================================================================

    /**
     * Fragmenta un secreto en n shares, de los cuales se necesitan
     * exactamente k para reconstruirlo.
     */
    public static List<BigInteger[]> split(BigInteger secret, int n, int k) {
        validateParams(secret, n, k);

        BigInteger[] coefficients = new BigInteger[k];
        coefficients[0] = secret;
        for (int i = 1; i < k; i++) {
            coefficients[i] = randomFieldElement();
        }

        List<BigInteger[]> shares = new ArrayList<>(n);
        for (int x = 1; x <= n; x++) {
            BigInteger xVal = BigInteger.valueOf(x);
            BigInteger yVal = evaluatePolynomial(coefficients, xVal);
            shares.add(new BigInteger[]{xVal, yVal});
        }

        clearArray(coefficients);
        return shares;
    }

    /**
     * Split que retorna shares SIN reducir mod p Y coefficients (sin limpiar memoria).
     * Usado por FeldmanVSS para calcular commitments del mismo polinomio.
     *
     * <p>Los shares se devuelven como enteros crudos (f(x) sin mod p) porque
     * Feldman VSS requiere que g^{share} ≡ ∏ C_i^{x^i} (mod p), y esta
     * igualdad solo se cumple cuando el exponente NO ha sido reducido mod p.
     * Para la reconstruccion via Lagrange, se reduce cada share mod p antes de interpolar.
     */
    public static Object[] splitWithCoefficients(BigInteger secret, int n, int k) {
        validateParams(secret, n, k);

        BigInteger[] coefficients = new BigInteger[k];
        coefficients[0] = secret;
        for (int i = 1; i < k; i++) {
            coefficients[i] = randomFieldElement();
        }

        List<BigInteger[]> shares = new ArrayList<>(n);
        for (int x = 1; x <= n; x++) {
            BigInteger xVal = BigInteger.valueOf(x);
            BigInteger yVal = evaluatePolynomialUnreduced(coefficients, xVal);
            shares.add(new BigInteger[]{xVal, yVal});
        }

        return new Object[]{shares, coefficients};
    }

    // =================================================================
    //  COMBINE -- Operacion RECONSTRUCT (interpolacion de Lagrange)
    // =================================================================

    /**
     * Reconstruye el secreto original desde al menos K shares usando
     * interpolacion de Lagrange sobre GF(p).
     */
    public static BigInteger combine(List<BigInteger[]> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un share.");
        }

        BigInteger secret = BigInteger.ZERO;

        for (int i = 0; i < shares.size(); i++) {
            BigInteger xi = shares.get(i)[0];
            BigInteger yi = shares.get(i)[1];

            BigInteger numerator   = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;

            for (int j = 0; j < shares.size(); j++) {
                if (i == j) continue;
                BigInteger xj = shares.get(j)[0];
                numerator   = numerator.multiply(xj.negate()).mod(PRIME);
                denominator = denominator.multiply(xi.subtract(xj)).mod(PRIME);
            }

            BigInteger lagrangeCoeff = numerator
                    .multiply(denominator.modInverse(PRIME))
                    .mod(PRIME);

            secret = secret
                    .add(yi.multiply(lagrangeCoeff))
                    .mod(PRIME);
        }

        return secret;
    }

    // =================================================================
    //  CONVERSION bytes <-> BigInteger
    // =================================================================

    public static BigInteger bytesToSecret(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes);
        if (value.compareTo(PRIME) >= 0) {
            throw new IllegalArgumentException(
                "El secreto (" + bytes.length * 8 + " bits) excede el campo GF(p). " +
                "Usar esquema hibrido para datos mayores de 65 bytes."
            );
        }
        return value;
    }

    public static byte[] secretToBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        byte[] result = new byte[length];

        if (raw.length <= length) {
            System.arraycopy(raw, 0, result, length - raw.length, raw.length);
        } else {
            System.arraycopy(raw, raw.length - length, result, 0, length);
        }

        return result;
    }

    // =================================================================
    //  PROACTIVE REFRESH
    // =================================================================

    /**
     * Genera un polinomio aleatorio de grado K-1 con termino constante igual a 0.
     *
     * <p>Usado en Proactive Share Refresh: cada nodo genera un polinomio r_i(x)
     * y calcula deltas = r_i(j) para repartir entre los nodos. Como r_i(0) = 0,
     * la suma de deltas preserva el secreto original.</p>
     *
     * @param k grado del polinomio + 1 (numero de coeficientes)
     * @return array de K BigInteger donde coefficients[0] = 0
     */
    public static BigInteger[] generateRefreshPolynomial(int k) {
        if (k < 2) {
            throw new IllegalArgumentException("K debe ser al menos 2.");
        }
        BigInteger[] coefficients = new BigInteger[k];
        coefficients[0] = BigInteger.ZERO;
        for (int i = 1; i < k; i++) {
            coefficients[i] = randomFieldElement();
        }
        return coefficients;
    }

    // =================================================================
    //  HELPERS PRIVADOS
    // =================================================================

    private static BigInteger evaluatePolynomial(BigInteger[] coefficients, BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients[i]).mod(PRIME);
        }
        return result;
    }

    /**
     * Evalua el polinomio SIN reducir mod p.
     * Usado por splitWithCoefficients para generar shares compatibles con Feldman VSS.
     */
    private static BigInteger evaluatePolynomialUnreduced(BigInteger[] coefficients, BigInteger x) {
        BigInteger result = BigInteger.ZERO;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients[i]);
        }
        return result;
    }

    public static BigInteger evaluatePolynomial(BigInteger[] coefficients, BigInteger x, BigInteger prime) {
        BigInteger result = BigInteger.ZERO;
        for (int i = coefficients.length - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients[i]).mod(prime);
        }
        return result;
    }

    private static BigInteger randomFieldElement() {
        BigInteger value;
        do {
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

    private static void clearArray(BigInteger[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = BigInteger.ZERO;
        }
    }
}
