package io.raindrops.core;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para {@link FeldmanVSS}.
 *
 * <p>Verifica la generacion de commitments, la validacion de shares
 * (validos y adulterados), la reconstruccion de secretos y el
 * comportamiento ante parametros invalidos.
 */
class FeldmanVSSTest {

    /**
     * Verifica que los commitments se generan correctamente y todos
     * los shares producidos por splitWithCoefficients pasan la verificacion.
     */
    @Test
    void computeCommitments_andVerifyShares() {
        BigInteger secret = new BigInteger("12345678901234567890");
        int n = 5, k = 3;

        Object[] split = ShamirSSS.splitWithCoefficients(secret, n, k);
        @SuppressWarnings("unchecked")
        List<BigInteger[]> shares = (List<BigInteger[]>) split[0];
        BigInteger[] coefficients = (BigInteger[]) split[1];

        List<BigInteger> commitments = FeldmanVSS.computeCommitments(coefficients);

        assertEquals(k, commitments.size());

        for (BigInteger[] share : shares) {
            int x = share[0].intValueExact();
            BigInteger y = share[1];
            assertTrue(FeldmanVSS.verifyShare(x, y, commitments),
                "Share x=" + x + " should be valid");
        }
    }

    /**
     * Verifica que un share adulterado (y incrementado en 1) es rechazado
     * por la verificacion de Feldman.
     */
    @Test
    void verifyShare_rejectsTamperedShare() {
        BigInteger secret = BigInteger.TWO.pow(128);
        Object[] split = ShamirSSS.splitWithCoefficients(secret, 3, 2);
        @SuppressWarnings("unchecked")
        List<BigInteger[]> shares = (List<BigInteger[]>) split[0];
        BigInteger[] coefficients = (BigInteger[]) split[1];

        List<BigInteger> commitments = FeldmanVSS.computeCommitments(coefficients);

        BigInteger[] tampered = shares.get(0);
        BigInteger tamperedY = tampered[1].add(BigInteger.ONE);

        assertFalse(FeldmanVSS.verifyShare(tampered[0].intValueExact(), tamperedY, commitments));
    }

    /**
     * Verifica que verifyShareOrThrow lanza InvalidShareException
     * cuando el share es invalido.
     */
    @Test
    void verifyShareOrThrow_throwsOnInvalidShare() {
        BigInteger secret = BigInteger.TWO.pow(128);
        Object[] split = ShamirSSS.splitWithCoefficients(secret, 3, 2);
        @SuppressWarnings("unchecked")
        List<BigInteger[]> shares = (List<BigInteger[]>) split[0];
        BigInteger[] coefficients = (BigInteger[]) split[1];

        List<BigInteger> commitments = FeldmanVSS.computeCommitments(coefficients);

        BigInteger[] tampered = shares.get(0);
        BigInteger tamperedY = tampered[1].add(BigInteger.ONE);

        assertThrows(FeldmanVSS.InvalidShareException.class, () ->
            FeldmanVSS.verifyShareOrThrow(tampered[0].intValueExact(), tamperedY, commitments));
    }

    /**
     * Verifica que despues de validar todos los shares con VSS,
     * la reconstruccion via ShamirSSS.combine() retorna el secreto original.
     */
    @Test
    void reconstruct_afterVSS_returnsOriginalSecret() {
        BigInteger secret = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
        int n = 4, k = 3;

        Object[] split = ShamirSSS.splitWithCoefficients(secret, n, k);
        @SuppressWarnings("unchecked")
        List<BigInteger[]> shares = (List<BigInteger[]>) split[0];
        BigInteger[] coefficients = (BigInteger[]) split[1];

        List<BigInteger> commitments = FeldmanVSS.computeCommitments(coefficients);

        // Verify all shares pass VSS
        for (BigInteger[] share : shares) {
            assertTrue(FeldmanVSS.verifyShare(share[0].intValueExact(), share[1], commitments));
        }

        // Reconstruct using SSS combine (same field)
        List<BigInteger[]> selectedShares = shares.subList(0, k);
        BigInteger reconstructed = ShamirSSS.combine(selectedShares);

        assertEquals(secret, reconstructed);
    }

    /**
     * Verifica que cada invocacion de splitWithCoefficients produce
     * commitments diferentes, y que los shares respectivos son validos
     * unicamente con sus propios commitments.
     */
    @Test
    void eachSplit_producesDifferentCommitments() {
        BigInteger secret = BigInteger.TWO.pow(128);

        Object[] split1 = ShamirSSS.splitWithCoefficients(secret, 3, 2);
        Object[] split2 = ShamirSSS.splitWithCoefficients(secret, 3, 2);

        @SuppressWarnings("unchecked")
        List<BigInteger> c1 = FeldmanVSS.computeCommitments((BigInteger[]) split1[1]);
        @SuppressWarnings("unchecked")
        List<BigInteger> c2 = FeldmanVSS.computeCommitments((BigInteger[]) split2[1]);

        assertNotEquals(c1, c2);

        @SuppressWarnings("unchecked")
        List<BigInteger[]> s1 = (List<BigInteger[]>) split1[0];
        @SuppressWarnings("unchecked")
        List<BigInteger[]> s2 = (List<BigInteger[]>) split2[0];

        for (BigInteger[] share : s1) {
            assertTrue(FeldmanVSS.verifyShare(share[0].intValueExact(), share[1], c1));
        }
        for (BigInteger[] share : s2) {
            assertTrue(FeldmanVSS.verifyShare(share[0].intValueExact(), share[1], c2));
        }
    }

    /**
     * Verifica que splitWithCoefficients lanza IllegalArgumentException
     * cuando los parametros n y k son invalidos.
     */
    @Test
    void invalidParams_throwException() {
        assertThrows(IllegalArgumentException.class, () ->
            ShamirSSS.splitWithCoefficients(BigInteger.ONE, 2, 3));
        assertThrows(IllegalArgumentException.class, () ->
            ShamirSSS.splitWithCoefficients(BigInteger.ONE, 1, 2));
    }

    /**
     * Verifica que una lista de commitments vacia o nula rechaza todos los shares.
     */
    @Test
    void emptyCommitments_rejectsAll() {
        assertFalse(FeldmanVSS.verifyShare(1, BigInteger.ONE, List.of()));
        assertFalse(FeldmanVSS.verifyShare(1, BigInteger.ONE, null));
    }
}
