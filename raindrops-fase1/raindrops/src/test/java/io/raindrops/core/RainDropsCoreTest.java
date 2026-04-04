package io.raindrops.core;

import org.junit.jupiter.api.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de integración del flujo completo DROP → RECONSTRUCT.
 *
 * Estos tests validan el comportamiento end-to-end de Fase 1
 * sin involucrar red: fragmentar → verificar → reconstruir en memoria.
 */
@DisplayName("RainDropsCore — Flujo completo DROP/RECONSTRUCT")
class RainDropsCoreTest {

    private static final SecureRandom RNG = new SecureRandom();

    // ════════════════════════════════════════════════════════════════════
    //  FLUJO BÁSICO
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Flujo básico")
    class FlujoBasico {

        @Test
        @DisplayName("Dato pequeño (≤65 bytes): DROP → RECONSTRUCT exacto")
        void smallDataDirectMode() {
            byte[] data = "Clave privada: abc123".getBytes();
            assertThat(data.length).isLessThanOrEqualTo(65);

            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 5, 3, 365);

            assertThat(rain.isDirectMode()).isTrue();
            assertThat(rain.getDrops()).hasSize(5);

            byte[] recovered = RainDropsCore.reconstruct(
                rain.getDrops(), rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(recovered).isEqualTo(data);
        }

        @Test
        @DisplayName("Dato grande (>65 bytes): DROP → RECONSTRUCT exacto (modo híbrido)")
        void largeDataHybridMode() {
            byte[] data = new byte[512];
            RNG.nextBytes(data);

            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 5, 3, 365);

            assertThat(rain.isDirectMode()).isFalse();
            assertThat(rain.getCiphertext()).isNotNull();

            byte[] recovered = RainDropsCore.reconstruct(
                rain.getDrops(), rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(recovered).isEqualTo(data);
        }

        @Test
        @DisplayName("Texto largo en UTF-8 sobrevive el ciclo completo")
        void utf8TextRoundTrip() {
            String text = "Historia clínica — Paciente: García López, Juan. " +
                          "Tipo sanguíneo: O+. Alergias: penicilina. " +
                          "Diagnóstico: hipertensión arterial grado II.";
            byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 7, 4, 365);
            byte[] recovered = RainDropsCore.reconstruct(
                rain.getDrops(), rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(new String(recovered, java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(text);
        }

        @Test
        @DisplayName("Archivo de 1 MB se fragmenta y reconstruye")
        void oneMegabyteFile() {
            byte[] data = new byte[1024 * 1024];
            RNG.nextBytes(data);

            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 7, 3, 365);
            byte[] recovered = RainDropsCore.reconstruct(
                rain.getDrops(), rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(recovered).isEqualTo(data);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  RESILIENCIA — Equivalente al test de Docker de las fases anteriores
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resiliencia ante pérdida de drops")
    class Resiliencia {

        @Test
        @DisplayName("Con N=7, K=3: perder 4 drops aún permite reconstruir")
        void toleratesLossOfNMinusK() {
            byte[] data = "Credencial secreta — wallet master key".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 7, 3, 365);

            // Simular pérdida de 4 nodos — quedan exactamente 3 (= K)
            List<Drop> surviving = rain.getDrops().subList(0, 3);

            byte[] recovered = RainDropsCore.reconstruct(
                surviving, rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(recovered).isEqualTo(data);
        }

        @Test
        @DisplayName("Más de K drops también reconstruye (usa solo los primeros K)")
        void moreThanKDropsWorks() {
            byte[] data = "Secreto importante".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 7, 3, 365);

            // Pasar los 7 drops — debe funcionar igual
            byte[] recovered = RainDropsCore.reconstruct(
                rain.getDrops(), rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            );

            assertThat(recovered).isEqualTo(data);
        }

        @Test
        @DisplayName("Con menos de K drops lanza QuorumException")
        void insufficientDropsThrows() {
            byte[] data = "Dato protegido".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 5, 3, 365);

            List<Drop> tooFew = rain.getDrops().subList(0, 2); // K-1 = 2

            assertThatThrownBy(() -> RainDropsCore.reconstruct(
                tooFew, rain.getMasterKey(),
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            ))
            .isInstanceOf(RainDropsCore.QuorumException.class)
            .hasMessageContaining("Quórum insuficiente");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  INTEGRIDAD — Drops adulterados
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Integridad de drops")
    class Integridad {

        @Test
        @DisplayName("Drop con masterKey incorrecta falla la verificación de MAC")
        void wrongMasterKeyDetected() {
            byte[] data = "Dato confidencial".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 5, 3, 365);

            // Clave maestra incorrecta
            byte[] wrongKey = new byte[32];
            new SecureRandom().nextBytes(wrongKey);

            assertThatThrownBy(() -> RainDropsCore.reconstruct(
                rain.getDrops(), wrongKey,
                rain.getCiphertext(), rain.getK(), rain.isDirectMode()
            ))
            .isInstanceOf(DropFactory.InvalidDropException.class)
            .hasMessageContaining("MAC inválido");
        }

        @Test
        @DisplayName("Drop verificado correctamente con masterKey original")
        void correctMasterKeyVerifies() {
            byte[] data = "Dato correcto".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 3, 2, 365);

            // Verificar cada drop individualmente
            for (Drop drop : rain.getDrops()) {
                assertThat(DropFactory.verify(drop, rain.getMasterKey()))
                    .as("Drop x=%d debe verificar correctamente", drop.getX())
                    .isTrue();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  TTL — Expiración de drops
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Expiración (TTL)")
    class Expiracion {

        @Test
        @DisplayName("Drop con TTL futuro no está expirado")
        void futureDropNotExpired() {
            byte[] data = "Dato vigente".getBytes();
            RainDropsCore.RainResult rain = RainDropsCore.drop(data, 3, 2, 365);

            for (Drop drop : rain.getDrops()) {
                assertThat(drop.isExpired())
                    .as("Drop con TTL a 1 año no debe estar expirado")
                    .isFalse();
                assertThat(drop.secondsUntilExpiry())
                    .as("Segundos restantes deben ser positivos")
                    .isPositive();
            }
        }

        @Test
        @DisplayName("Drop con TTL en el pasado está expirado")
        void expiredDropDetected() {
            // Crear drop directamente con TTL en el pasado
            byte[] masterKey = new byte[32];
            RNG.nextBytes(masterKey);
            long pastTtl = System.currentTimeMillis() / 1000 - 1; // hace 1 segundo

            Drop expired = DropFactory.create(1, java.math.BigInteger.TEN, masterKey, pastTtl);

            assertThat(expired.isExpired()).isTrue();
            assertThat(DropFactory.verify(expired, masterKey))
                .as("Drop expirado debe fallar la verificación")
                .isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  INDEPENDENCIA — Distintas lluvias son independientes
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Drops de lluvias distintas no se mezclan (masterKeys distintas)")
    void differentRainsAreIndependent() {
        byte[] data1 = "Secreto lluvia 1".getBytes();
        byte[] data2 = "Secreto lluvia 2".getBytes();

        RainDropsCore.RainResult rain1 = RainDropsCore.drop(data1, 5, 3, 365);
        RainDropsCore.RainResult rain2 = RainDropsCore.drop(data2, 5, 3, 365);

        // Intentar usar drops de lluvia1 con masterKey de lluvia2
        assertThatThrownBy(() -> RainDropsCore.reconstruct(
            rain1.getDrops(), rain2.getMasterKey(),
            rain2.getCiphertext(), rain2.getK(), rain2.isDirectMode()
        ))
        .isInstanceOf(DropFactory.InvalidDropException.class);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VALIDACIÓN DE PARÁMETROS
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("drop() con datos nulos lanza IllegalArgumentException")
    void nullDataThrows() {
        assertThatThrownBy(() -> RainDropsCore.drop(null, 5, 3, 365))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("drop() con ttlDays < 1 lanza IllegalArgumentException")
    void invalidTtlThrows() {
        assertThatThrownBy(() -> RainDropsCore.drop("dato".getBytes(), 5, 3, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
