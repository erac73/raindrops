package io.raindrops.core;

import org.junit.jupiter.api.*;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests del esquema híbrido AES-256-GCM + SSS.
 */
@DisplayName("HybridScheme — AES-256-GCM")
class HybridSchemeTest {

    private static final SecureRandom RNG = new SecureRandom();

    @Test
    @DisplayName("encrypt → decrypt recupera el plaintext original")
    void encryptDecryptRoundTrip() {
        byte[] original = "Historia clínica confidencial — tipo sanguíneo O+".getBytes();

        HybridScheme.EncryptionResult result = HybridScheme.encrypt(original);
        byte[] decrypted = HybridScheme.decrypt(result.getAesKey(), result.getCiphertext());

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Funciona con datos grandes (1 MB)")
    void largePlaintext() {
        byte[] large = new byte[1024 * 1024]; // 1 MB
        RNG.nextBytes(large);

        HybridScheme.EncryptionResult result = HybridScheme.encrypt(large);
        byte[] decrypted = HybridScheme.decrypt(result.getAesKey(), result.getCiphertext());

        assertThat(decrypted).isEqualTo(large);
    }

    @Test
    @DisplayName("Dos cifrados del mismo plaintext producen ciphertexts distintos (IND)")
    void twoEncryptionsDiffer() {
        byte[] data = "Secreto importante".getBytes();

        HybridScheme.EncryptionResult r1 = HybridScheme.encrypt(data);
        HybridScheme.EncryptionResult r2 = HybridScheme.encrypt(data);

        // El nonce aleatorio garantiza ciphertexts distintos
        assertThat(r1.getCiphertext()).isNotEqualTo(r2.getCiphertext());
        // Pero ambos descifran al mismo plaintext
        assertThat(HybridScheme.decrypt(r1.getAesKey(), r1.getCiphertext())).isEqualTo(data);
        assertThat(HybridScheme.decrypt(r2.getAesKey(), r2.getCiphertext())).isEqualTo(data);
    }

    @Test
    @DisplayName("Clave incorrecta lanza CryptoException (GCM tag inválido)")
    void wrongKeyThrows() {
        byte[] data = "Dato privado".getBytes();
        HybridScheme.EncryptionResult result = HybridScheme.encrypt(data);

        byte[] wrongKey = new byte[32];
        RNG.nextBytes(wrongKey);

        assertThatThrownBy(() -> HybridScheme.decrypt(wrongKey, result.getCiphertext()))
            .isInstanceOf(HybridScheme.CryptoException.class)
            .hasMessageContaining("authentication tag inválido");
    }

    @Test
    @DisplayName("Ciphertext adulterado lanza CryptoException")
    void tamperedCiphertextThrows() {
        byte[] data = "Dato privado".getBytes();
        HybridScheme.EncryptionResult result = HybridScheme.encrypt(data);

        byte[] tampered = result.getCiphertext().clone();
        tampered[tampered.length - 1] ^= 0xFF; // flip del último byte

        assertThatThrownBy(() -> HybridScheme.decrypt(result.getAesKey(), tampered))
            .isInstanceOf(HybridScheme.CryptoException.class);
    }

    @Test
    @DisplayName("close() limpia la clave AES de EncryptionResult")
    void closeZeroizesKey() {
        byte[] data = "Dato".getBytes();
        HybridScheme.EncryptionResult result = HybridScheme.encrypt(data);
        byte[] keyBefore = result.getAesKey(); // copia antes de close

        result.close();

        // La clave interna debe estar zerozada (no visible desde getAesKey()
        // porque getAesKey() hace clone() — verificamos que el original fue limpiado
        // usando reflexión no es necesario aquí; la prueba valida el comportamiento público)
        byte[] keyAfterCopy = result.getAesKey();

        // Después de close(), la clave retornada por getAesKey() debe ser ceros
        // (porque internamente el array original fue llenado con 0x00)
        assertThat(keyAfterCopy).containsOnly((byte) 0);
    }
}
