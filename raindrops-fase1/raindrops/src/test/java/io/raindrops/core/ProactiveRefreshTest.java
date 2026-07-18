package io.raindrops.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests del Proactive Share Refresh (IMP-002).
 *
 * Cobertura:
 * - generateRefreshPolynomial genera polinomios validos (termino constante = 0)
 * - Despues del refresh, K shares reconstruyen el mismo secreto
 * - Distintas rondas de refresh producen shares diferentes
 * - El secreto no cambia despues de multiples rondas de refresh
 * - Simulacion completa de refresh entre N nodos
 */
@DisplayName("Proactive Share Refresh — IMP-002")
class ProactiveRefreshTest {

    private static final SecureRandom RNG = new SecureRandom();

    // ════════════════════════════════════════════════════════════════════
    //  generateRefreshPolynomial
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Generacion de polinomio de refresh")
    class PolynomialTests {

        @Test
        @DisplayName("El polinomio tiene K coeficientes con termnulo constante = 0")
        void polynomialHasCorrectLength() {
            BigInteger[] poly = ShamirSSS.generateRefreshPolynomial(3);
            assertThat(poly).hasSize(3);
            assertThat(poly[0]).isEqualTo(BigInteger.ZERO);
        }

        @Test
        @DisplayName("Los coeficientes no nulos son element del campo GF(p)")
        void coefficientsInField() {
            BigInteger[] poly = ShamirSSS.generateRefreshPolynomial(5);
            for (int i = 1; i < poly.length; i++) {
                assertThat(poly[i]).isGreaterThanOrEqualTo(BigInteger.ZERO);
                assertThat(poly[i]).isLessThan(ShamirSSS.PRIME);
            }
        }

        @Test
        @DisplayName("Polinomios distintos generados son diferentes")
        void distinctPolynomialsAreDifferent() {
            BigInteger[] poly1 = ShamirSSS.generateRefreshPolynomial(3);
            BigInteger[] poly2 = ShamirSSS.generateRefreshPolynomial(3);

            boolean allSame = true;
            for (int i = 0; i < poly1.length; i++) {
                if (!poly1[i].equals(poly2[i])) {
                    allSame = false;
                    break;
                }
            }
            assertThat(allSame)
                .as("Dos polinomios aleatorios deben diferir con alta probabilidad")
                .isFalse();
        }

        @Test
        @DisplayName("K < 2 lanza IllegalArgumentException")
        void kLessThanTwoThrows() {
            assertThatThrownBy(() -> ShamirSSS.generateRefreshPolynomial(1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  CORRECTITUD DEL REFRESH
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Correctitud del refresh")
    class RefreshCorrectnessTests {

        @Test
        @DisplayName("Despues de refresh, K shares reconstruyen el mismo secreto")
        void refreshPreservesSecret() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            int n = 5, k = 3;

            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            BigInteger[] refreshPoly = ShamirSSS.generateRefreshPolynomial(k);
            List<BigInteger[]> deltas = computeDeltas(refreshPoly, n);

            List<BigInteger[]> refreshedShares = applyDeltas(shares, deltas);

            BigInteger reconstructed = ShamirSSS.combine(refreshedShares.subList(0, k));
            assertThat(reconstructed)
                .as("El secreto debe preservarse despues del refresh")
                .isEqualTo(secret);
        }

        @Test
        @DisplayName("Multiples rondas de refresh preservan el secreto")
        void multipleRefreshRoundsPreservesSecret() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            int n = 5, k = 3;

            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            for (int round = 0; round < 10; round++) {
                BigInteger[] refreshPoly = ShamirSSS.generateRefreshPolynomial(k);
                List<BigInteger[]> deltas = computeDeltas(refreshPoly, n);
                shares = applyDeltas(shares, deltas);

                BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, k));
                assertThat(reconstructed)
                    .as("Ronda %d: el secreto debe preservarse", round + 1)
                    .isEqualTo(secret);
            }
        }

        @Test
        @DisplayName("Distintas rondas de refresh producen shares diferentes")
        void differentRefreshRoundsProduceDifferentShares() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            int n = 5, k = 3;

            List<BigInteger[]> shares1 = ShamirSSS.split(secret, n, k);
            List<BigInteger[]> shares2 = new ArrayList<>(shares1);

            BigInteger[] poly1 = ShamirSSS.generateRefreshPolynomial(k);
            shares1 = applyDeltas(shares1, computeDeltas(poly1, n));

            BigInteger[] poly2 = ShamirSSS.generateRefreshPolynomial(k);
            shares2 = applyDeltas(shares2, computeDeltas(poly2, n));

            boolean allSame = true;
            for (int i = 0; i < n; i++) {
                if (!shares1.get(i)[1].equals(shares2.get(i)[1])) {
                    allSame = false;
                    break;
                }
            }

            assertThat(allSame)
                .as("Shares de distintas rondas deben diferir con alta probabilidad")
                .isFalse();
        }

        @ParameterizedTest(name = "N={0}, K={1}")
        @CsvSource({"5,3", "7,4", "3,2", "10,6"})
        @DisplayName("Refresh preserva el secreto para distintas combinaciones (N,K)")
        void refreshForMultipleNK(int n, int k) {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            BigInteger[] refreshPoly = ShamirSSS.generateRefreshPolynomial(k);
            List<BigInteger[]> deltas = computeDeltas(refreshPoly, n);
            List<BigInteger[]> refreshedShares = applyDeltas(shares, deltas);

            BigInteger reconstructed = ShamirSSS.combine(refreshedShares.subList(0, k));
            assertThat(reconstructed)
                .as("N=%d, K=%d: secreto preservado", n, k)
                .isEqualTo(secret);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  SIMULACION DE MULTI-NODO
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Simulacion multi-nodo")
    class MultiNodeTests {

        @Test
        @DisplayName("Cada nodo genera su propio polinomio y deltas se aplican correctamente")
        void multiNodeRefreshSimulation() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            int n = 4, k = 3;

            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            List<List<BigInteger[]>> allDeltas = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                BigInteger[] poly = ShamirSSS.generateRefreshPolynomial(k);
                allDeltas.add(computeDeltas(poly, n));
            }

            for (int nodeIdx = 0; nodeIdx < n; nodeIdx++) {
                BigInteger[] currentShare = shares.get(nodeIdx);
                BigInteger newY = currentShare[1];

                for (int senderIdx = 0; senderIdx < n; senderIdx++) {
                    BigInteger delta = allDeltas.get(senderIdx).get(nodeIdx)[1];
                    newY = newY.add(delta).mod(ShamirSSS.PRIME);
                }

                shares.set(nodeIdx, new BigInteger[]{currentShare[0], newY});
            }

            BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, k));
            assertThat(reconstructed)
                .as("Despues de refresh multi-nodo, el secreto debe preservarse")
                .isEqualTo(secret);
        }

        @Test
        @DisplayName("Simulacion de multiples rondas multi-nodo")
        void multiNodeMultipleRounds() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            int n = 5, k = 3;

            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            for (int round = 0; round < 5; round++) {
                List<List<BigInteger[]>> allDeltas = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    BigInteger[] poly = ShamirSSS.generateRefreshPolynomial(k);
                    allDeltas.add(computeDeltas(poly, n));
                }

                for (int nodeIdx = 0; nodeIdx < n; nodeIdx++) {
                    BigInteger[] currentShare = shares.get(nodeIdx);
                    BigInteger newY = currentShare[1];

                    for (int senderIdx = 0; senderIdx < n; senderIdx++) {
                        BigInteger delta = allDeltas.get(senderIdx).get(nodeIdx)[1];
                        newY = newY.add(delta).mod(ShamirSSS.PRIME);
                    }

                    shares.set(nodeIdx, new BigInteger[]{currentShare[0], newY});
                }

                BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, k));
                assertThat(reconstructed)
                    .as("Ronda %d multi-nodo: secreto preservado", round + 1)
                    .isEqualTo(secret);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private List<BigInteger[]> computeDeltas(BigInteger[] polynomial, int n) {
        List<BigInteger[]> deltas = new ArrayList<>();
        for (int j = 1; j <= n; j++) {
            BigInteger delta = ShamirSSS.evaluatePolynomial(
                    polynomial, BigInteger.valueOf(j), ShamirSSS.PRIME);
            deltas.add(new BigInteger[]{BigInteger.valueOf(j), delta});
        }
        return deltas;
    }

    private List<BigInteger[]> applyDeltas(List<BigInteger[]> shares, List<BigInteger[]> deltas) {
        List<BigInteger[]> result = new ArrayList<>(shares.size());
        for (int i = 0; i < shares.size(); i++) {
            BigInteger x = shares.get(i)[0];
            BigInteger y = shares.get(i)[1];
            BigInteger deltaY = deltas.get(i)[1];
            BigInteger newY = y.add(deltaY).mod(ShamirSSS.PRIME);
            result.add(new BigInteger[]{x, newY});
        }
        return result;
    }
}
