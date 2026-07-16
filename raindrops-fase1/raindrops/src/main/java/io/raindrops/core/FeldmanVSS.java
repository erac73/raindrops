package io.raindrops.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Feldman's Verifiable Secret Sharing (VSS).
 *
 * <p>Opera en el MISMO campo GF(p) que ShamirSSS (p = 2^521 - 1).
 * Generador g = 2. Los coefficients de SSS se usan directamente para
 * calcular commitments: C_i = g^a_i mod p.
 *
 * <p>Verificacion: g^y ≡ ∏ C_i^{x^i} mod p
 *
 * <p>Seguridad: el discrete log en Z_p* es hard para primo de Mersenne.
 * Los shares son element de GF(p), los commitments tambien.
 *
 * @see ShamirSSS
 */
public final class FeldmanVSS {

    private static final BigInteger PRIME = ShamirSSS.PRIME;
    private static final BigInteger GENERATOR = BigInteger.TWO;

    private FeldmanVSS() {}

    // ════════════════════════════════════════════════════════════════════
    //  COMMITMENTS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Genera commitments de Feldman a partir de los coefficients del polinomio SSS.
     * C_i = g^a_i mod p, donde a_i son los coefficients del polinomio GF(p).
     *
     * @param coefficients  Coefficients del polinomio SSS: [a_0, a_1, ..., a_{k-1}]
     * @return              Lista de commitments: [C_0, C_1, ..., C_{k-1}]
     */
    public static List<BigInteger> computeCommitments(BigInteger[] coefficients) {
        if (coefficients == null || coefficients.length == 0) {
            throw new IllegalArgumentException("Coefficients cannot be null or empty.");
        }
        List<BigInteger> commitments = new ArrayList<>(coefficients.length);
        for (BigInteger coeff : coefficients) {
            commitments.add(GENERATOR.modPow(coeff, PRIME));
        }
        return commitments;
    }

    // ════════════════════════════════════════════════════════════════════
    //  VERIFICATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Verifica que un share de SSS es matematicamente consistente con los commitments.
     *
     * <p>Condicion de Feldman: g^y ≡ ∏ C_i^{x^i} mod p
     *
     * @param x            Coordenada x del share (1-based).
     * @param y            Coordenada y del share = f(x) mod p.
     * @param commitments  Lista de commitments del dealer.
     * @return             true si el share es valido.
     */
    public static boolean verifyShare(int x, BigInteger y, List<BigInteger> commitments) {
        if (commitments == null || commitments.isEmpty()) return false;

        int k = commitments.size();

        // LHS: g^y mod p
        BigInteger lhs = GENERATOR.modPow(y, PRIME);

        // RHS: ∏ C_i^{x^i} mod p
        BigInteger rhs = BigInteger.ONE;
        BigInteger xPower = BigInteger.ONE;
        for (int i = 0; i < k; i++) {
            rhs = rhs.multiply(commitments.get(i).modPow(xPower, PRIME)).mod(PRIME);
            xPower = xPower.multiply(BigInteger.valueOf(x)).mod(PRIME);
        }

        return lhs.equals(rhs);
    }

    /**
     * Verifica share y lanza excepcion si es invalido.
     *
     * @param x            Coordenada x del share (1-based).
     * @param y            Coordenada y del share = f(x) mod p.
     * @param commitments  Lista de commitments del dealer.
     * @throws InvalidShareException si el share no es consistente con los commitments.
     */
    public static void verifyShareOrThrow(int x, BigInteger y, List<BigInteger> commitments) {
        if (!verifyShare(x, y, commitments)) {
            throw new InvalidShareException(
                "Share verification FAILED for x=" + x +
                ". Mathematically inconsistent with commitments.");
        }
    }

    /**
     * Verifica todos los shares de una lista y retorna solo los validos.
     *
     * @param shares       Lista de shares como BigInteger[]{x, y}.
     * @param commitments  Lista de commitments.
     * @return             Lista de shares validos.
     */
    public static List<BigInteger[]> verifyAll(List<BigInteger[]> shares,
                                               List<BigInteger> commitments) {
        List<BigInteger[]> valid = new ArrayList<>();
        for (BigInteger[] share : shares) {
            int x = share[0].intValueExact();
            BigInteger y = share[1];
            if (verifyShare(x, y, commitments)) {
                valid.add(share);
            }
        }
        return valid;
    }

    // ════════════════════════════════════════════════════════════════════
    //  COMBINE — Reconstruccion sobre GF(p) (mismo campo que ShamirSSS)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Reconstruye el secreto desde K shares usando interpolacion de Lagrange.
     * Opera en GF(p) — exactamente igual que ShamirSSS.combine().
     *
     * @param shares  Lista de shares como BigInteger[]{x, y}.
     * @return        El secreto reconstruido.
     */
    public static BigInteger combine(List<BigInteger[]> shares) {
        return ShamirSSS.combine(shares);
    }

    // ── Excepciones ───────────────────────────────────────────────────

    /**
     * Excepcion lanzada cuando un share no es validado por los commitments de Feldman.
     */
    public static class InvalidShareException extends RuntimeException {
        /**
         * Crea una nueva InvalidShareException con el mensaje dado.
         *
         * @param message  Descripcion del fallo de verificacion.
         */
        public InvalidShareException(String message) { super(message); }
    }
}
