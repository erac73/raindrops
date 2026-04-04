package io.raindrops.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests del núcleo SSS.
 *
 * Cobertura:
 * - Correctitud matemática (split → combine = secreto original)
 * - Seguridad: K-1 shares no revelan el secreto
 * - Robustez: parámetros inválidos, shares en distinto orden
 * - Conversión bytes ↔ BigInteger
 */
@DisplayName("ShamirSSS — Núcleo criptográfico")
class ShamirSSSTest {

    private static final SecureRandom RNG = new SecureRandom();

    // ════════════════════════════════════════════════════════════════════
    //  CORRECTITUD
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Correctitud matemática")
    class CorrecitudTests {

        @Test
        @DisplayName("split(3,5) → combine con 3 shares exactos reconstruye el secreto")
        void splitCombineExact() {
            BigInteger secret = new BigInteger(200, RNG);
            secret = secret.mod(ShamirSSS.PRIME); // garantizar S < p

            List<BigInteger[]> shares = ShamirSSS.split(secret, 5, 3);
            List<BigInteger[]> subset = shares.subList(0, 3); // primeros 3

            BigInteger reconstructed = ShamirSSS.combine(subset);
            assertThat(reconstructed).isEqualTo(secret);
        }

        @ParameterizedTest(name = "N={0}, K={1}")
        @CsvSource({"5,3", "7,4", "10,6", "3,2", "7,7"})
        @DisplayName("Distintas combinaciones (N,K) reconstruyen correctamente")
        void multipleNKCombinations(int n, int k) {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            List<BigInteger[]> shares = ShamirSSS.split(secret, n, k);

            // Usar exactamente K shares
            BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, k));
            assertThat(reconstructed)
                .as("N=%d, K=%d debe reconstruir el secreto con %d shares", n, k, k)
                .isEqualTo(secret);
        }

        @Test
        @DisplayName("Cualquier subconjunto de K shares reconstruye el mismo secreto")
        void anyKSubsetReconstructsSameSecret() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            List<BigInteger[]> shares = ShamirSSS.split(secret, 7, 3);

            // Distintas combinaciones de 3 shares del total de 7
            BigInteger r1 = ShamirSSS.combine(List.of(shares.get(0), shares.get(1), shares.get(2)));
            BigInteger r2 = ShamirSSS.combine(List.of(shares.get(2), shares.get(4), shares.get(6)));
            BigInteger r3 = ShamirSSS.combine(List.of(shares.get(1), shares.get(3), shares.get(5)));

            assertThat(r1).isEqualTo(secret);
            assertThat(r2).isEqualTo(secret);
            assertThat(r3).isEqualTo(secret);
        }

        @Test
        @DisplayName("El orden de los shares no afecta el resultado")
        void sharesOrderIndependent() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            List<BigInteger[]> shares = new ArrayList<>(ShamirSSS.split(secret, 5, 3));

            List<BigInteger[]> shuffled = new ArrayList<>(shares.subList(0, 3));
            Collections.shuffle(shuffled);

            BigInteger reconstructed = ShamirSSS.combine(shuffled);
            assertThat(reconstructed).isEqualTo(secret);
        }

        @Test
        @DisplayName("Secreto = 0 es válido (caso borde)")
        void zeroSecretIsValid() {
            BigInteger secret = BigInteger.ZERO;
            List<BigInteger[]> shares = ShamirSSS.split(secret, 3, 2);
            BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, 2));
            assertThat(reconstructed).isEqualTo(BigInteger.ZERO);
        }

        @Test
        @DisplayName("Secreto = PRIME-1 es válido (caso borde)")
        void maxSecretIsValid() {
            BigInteger secret = ShamirSSS.PRIME.subtract(BigInteger.ONE);
            List<BigInteger[]> shares = ShamirSSS.split(secret, 3, 2);
            BigInteger reconstructed = ShamirSSS.combine(shares.subList(0, 2));
            assertThat(reconstructed).isEqualTo(secret);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  SEGURIDAD — Propiedad de seguridad perfecta (Teorema 1)
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Seguridad perfecta (Teorema 1)")
    class SeguridadTests {

        @Test
        @DisplayName("K-1 shares no deben producir el secreto correcto")
        void kMinusOneSharesCannotRecoverSecret() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
            List<BigInteger[]> shares = ShamirSSS.split(secret, 5, 3);

            // Intentar reconstruir con solo 2 shares (K-1 = 3-1 = 2)
            BigInteger wrongResult = ShamirSSS.combine(shares.subList(0, 2));

            // El resultado NO debe ser el secreto original
            // (con alta probabilidad — la probabilidad de colisión es 1/p ≈ 2^-521)
            assertThat(wrongResult)
                .as("K-1 shares no deben reconstruir el secreto original")
                .isNotEqualTo(secret);
        }

        @Test
        @DisplayName("Dos lluvias distintas del mismo secreto producen shares distintos")
        void sameSecretDifferentShares() {
            BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);

            List<BigInteger[]> shares1 = ShamirSSS.split(secret, 3, 2);
            List<BigInteger[]> shares2 = ShamirSSS.split(secret, 3, 2);

            // Los shares deben ser distintos (polinomios aleatorios distintos)
            // x es determinista (1,2,3), pero y debe diferir
            assertThat(shares1.get(0)[1])
                .as("Los valores y de shares distintos deben diferir con alta probabilidad")
                .isNotEqualTo(shares2.get(0)[1]);
        }

        @Test
        @DisplayName("Un único share no revela información sobre el secreto")
        void singleShareRevealsNothing() {
            // Con K=3, un solo share debe ser indistinguible de un valor aleatorio
            // Verificamos que combine(1 share) ≠ secreto para 100 secretos distintos
            int falsePosCount = 0;
            for (int trial = 0; trial < 100; trial++) {
                BigInteger secret = new BigInteger(200, RNG).mod(ShamirSSS.PRIME);
                List<BigInteger[]> shares = ShamirSSS.split(secret, 5, 3);
                BigInteger guess = ShamirSSS.combine(List.of(shares.get(0)));
                if (guess.equals(secret)) falsePosCount++;
            }
            // Con 100 trials, esperamos 0 colisiones (probabilidad ≈ 100/2^521)
            assertThat(falsePosCount)
                .as("Ningún share único debe reconstruir el secreto")
                .isZero();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONVERSIÓN BYTES
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Conversión bytes ↔ BigInteger")
    class ConversionTests {

        @Test
        @DisplayName("Clave AES de 32 bytes se convierte y recupera exactamente")
        void aesKeyRoundTrip() {
            byte[] aesKey = new byte[32];
            RNG.nextBytes(aesKey);

            BigInteger secret       = ShamirSSS.bytesToSecret(aesKey);
            byte[]     recovered    = ShamirSSS.secretToBytes(secret, 32);

            assertThat(recovered).isEqualTo(aesKey);
        }

        @Test
        @DisplayName("Clave AES sobrevive un ciclo split-combine completo")
        void aesKeyThroughSplitCombine() {
            byte[] originalKey = new byte[32];
            RNG.nextBytes(originalKey);

            BigInteger secret        = ShamirSSS.bytesToSecret(originalKey);
            List<BigInteger[]> shares = ShamirSSS.split(secret, 5, 3);
            BigInteger recovered     = ShamirSSS.combine(shares.subList(0, 3));
            byte[] recoveredKey      = ShamirSSS.secretToBytes(recovered, 32);

            assertThat(recoveredKey).isEqualTo(originalKey);
        }

        @Test
        @DisplayName("bytesToSecret lanza si el valor supera PRIME")
        void oversizedSecretThrows() {
            byte[] tooBig = new byte[66]; // > 65 bytes max
            tooBig[0] = (byte) 0xFF;     // asegurar que supera PRIME

            assertThatThrownBy(() -> ShamirSSS.bytesToSecret(tooBig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede el campo");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  VALIDACIÓN DE PARÁMETROS
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validación de parámetros")
    class ValidacionTests {

        @Test
        @DisplayName("K < 2 lanza IllegalArgumentException")
        void kLessThanTwoThrows() {
            assertThatThrownBy(() -> ShamirSSS.split(BigInteger.TEN, 3, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("K debe ser al menos 2");
        }

        @Test
        @DisplayName("N < K lanza IllegalArgumentException")
        void nLessThanKThrows() {
            assertThatThrownBy(() -> ShamirSSS.split(BigInteger.TEN, 2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("N debe ser >= K");
        }

        @Test
        @DisplayName("Secreto negativo lanza IllegalArgumentException")
        void negativeSecretThrows() {
            assertThatThrownBy(() -> ShamirSSS.split(BigInteger.ONE.negate(), 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Secreto >= PRIME lanza IllegalArgumentException")
        void secretExceedsPrimeThrows() {
            assertThatThrownBy(() -> ShamirSSS.split(ShamirSSS.PRIME, 3, 2))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("combine con lista vacía lanza IllegalArgumentException")
        void emptySharesThrows() {
            assertThatThrownBy(() -> ShamirSSS.combine(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
