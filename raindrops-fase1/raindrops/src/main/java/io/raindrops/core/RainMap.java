package io.raindrops.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapa de Rain Drops — cifrado y mapeo drop→nodo.
 *
 * <p>Contiene la información mínima para reconstruir el dato:
 * <ul>
 *   <li>{@code urlIndex} — mapeo dropId → nodeUrl</li>
 *   <li>{@code n, k} — parámetros de umbral</li>
 *   <li>{@code commitments} — commitments de Feldman VSS (opcional)</li>
 * </ul>
 *
 * <p>El Rain Map siempre está cifrado con AES-256-GCM usando la masterKey.
 * Solo el poseedor de la masterKey puede descifrarlo y acceder a los drops.</p>
 */
public final class RainMap {

    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<String, String> urlIndex;
    private final List<BigInteger> commitments;
    private byte[] encryptedPayload;
    private byte[] nonce;
    private final int n;
    private final int k;

    private RainMap(Map<String, String> urlIndex, List<BigInteger> commitments,
                    byte[] encryptedPayload, byte[] nonce, int n, int k) {
        this.urlIndex = urlIndex != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(urlIndex))
                : null;
        this.commitments = commitments != null
                ? List.copyOf(commitments)
                : null;
        this.encryptedPayload = encryptedPayload;
        this.nonce = nonce;
        this.n = n;
        this.k = k;
    }

    /**
     * Crea un Rain Map con commitments de Feldman VSS.
     *
     * @param drops        Lista de drops a mapear.
     * @param nodeUrls     URLs de los nodos Storage (mismo orden que drops).
     * @param masterKey    Clave maestra para cifrar (32 bytes).
     * @param k            Umbral de reconstrucción.
     * @param commitments  Commitments de Feldman VSS (puede ser null para modo legacy).
     * @return             Rain Map cifrado y listo para uso.
     */
    public static RainMap create(List<Drop> drops, List<String> nodeUrls,
                                 byte[] masterKey, int k, List<BigInteger> commitments) {
        if (drops.size() != nodeUrls.size()) {
            throw new IllegalArgumentException("drops and nodeUrls must have same size");
        }
        if (masterKey == null || masterKey.length != 32) {
            throw new IllegalArgumentException("masterKey must be 32 bytes");
        }

        HexFormat hex = HexFormat.of();
        Map<String, String> index = new LinkedHashMap<>();
        for (int i = 0; i < drops.size(); i++) {
            index.put(hex.formatHex(drops.get(i).getId()), nodeUrls.get(i));
        }

        int n = nodeUrls.size();
        if (k < 1 || k > n) {
            throw new IllegalArgumentException("k must be between 1 and " + n);
        }

        RainMap map = new RainMap(index, commitments, null, null, n, k);
        map.seal(masterKey);
        return map;
    }

    /**
     * Crea un Rain Map sin commitments (modo legacy, sin VSS).
     *
     * @param drops     Lista de drops a mapear.
     * @param nodeUrls  URLs de los nodos Storage (mismo orden que drops).
     * @param masterKey Clave maestra para cifrar (32 bytes).
     * @param k         Umbral de reconstrucción.
     * @return          Rain Map cifrado sin commitments.
     */
    public static RainMap create(List<Drop> drops, List<String> nodeUrls,
                                 byte[] masterKey, int k) {
        return create(drops, nodeUrls, masterKey, k, null);
    }

    /**
     * Descifra un Rain Map desde su payload combinado.
     *
     * @param combinedPayload  nonce(12B) ‖ ciphertext.
     * @param masterKey        Clave maestra para descifrar.
     * @return                 Rain Map descifrado.
     */
    public static RainMap fromEncrypted(byte[] combinedPayload, byte[] masterKey) {
        byte[] nonce = Arrays.copyOfRange(combinedPayload, 0, NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combinedPayload, NONCE_BYTES, combinedPayload.length);

        byte[] jsonBytes = aesGcmDecrypt(masterKey, nonce, ciphertext);
        JsonNode root;
        try {
            root = MAPPER.readTree(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse sealed payload", e);
        }

        Map<String, String> urlIndex = MAPPER.convertValue(root.get("urlIndex"),
                new TypeReference<Map<String, String>>() {});
        int n = root.get("n").asInt();
        int k = root.get("k").asInt();

        // Leer commitments si existen (backward compatible)
        List<BigInteger> commitments = null;
        JsonNode commitmentsNode = root.get("commitments");
        if (commitmentsNode != null && commitmentsNode.isArray()) {
            commitments = new java.util.ArrayList<>();
            for (JsonNode c : commitmentsNode) {
                commitments.add(new BigInteger(c.asText()));
            }
        }

        return new RainMap(urlIndex, commitments, ciphertext, nonce, n, k);
    }

    /**
     * Cifra el Rain Map con AES-256-GCM.
     *
     * @param masterKey  Clave maestra (32 bytes).
     * @return           Payload combinado: nonce(12B) ‖ ciphertext.
     */
    public byte[] seal(byte[] masterKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("urlIndex", urlIndex);
        payload.put("n", n);
        payload.put("k", k);
        // Almacenar commitments como strings (BigIntegers son muy grandes para JSON directo)
        if (commitments != null) {
            List<String> commitmentStrings = new java.util.ArrayList<>();
            for (BigInteger c : commitments) {
                commitmentStrings.add(c.toString());
            }
            payload.put("commitments", commitmentStrings);
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }

        byte[] plaintext = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);

        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters params = new AEADParameters(new KeyParameter(masterKey), GCM_TAG_BITS, nonce);
        cipher.init(true, params);

        byte[] output = new byte[cipher.getOutputSize(plaintext.length)];
        int offset = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
        try {
            cipher.doFinal(output, offset);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encryption failed", e);
        }

        this.encryptedPayload = output;

        byte[] result = new byte[NONCE_BYTES + output.length];
        System.arraycopy(nonce, 0, result, 0, NONCE_BYTES);
        System.arraycopy(output, 0, result, NONCE_BYTES, output.length);
        return result;
    }

    /**
     * Descifra el Rain Map y retorna el índice de drops (dropId → nodeUrl).
     *
     * @param masterKey Clave maestra para descifrar (32 bytes).
     * @return          Mapa con el índice de drops descifrado.
     */
    public Map<String, String> unseal(byte[] masterKey) {
        byte[] jsonBytes = aesGcmDecrypt(masterKey, nonce, encryptedPayload);
        JsonNode root;
        try {
            root = MAPPER.readTree(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse sealed payload", e);
        }
        return MAPPER.convertValue(root.get("urlIndex"),
                new TypeReference<Map<String, String>>() {});
    }

    /**
     * Obtiene la URL del nodo Storage donde se almacena un drop específico.
     *
     * @param dropId identificador del drop (hexadecimal).
     * @return       URL del nodo Storage que almacena el drop.
     * @throws IllegalStateException si el RainMap no ha sido descifrado previamente.
     */
    public String getNodeUrl(String dropId) {
        if (urlIndex == null) {
            throw new IllegalStateException("urlIndex not available");
        }
        return urlIndex.get(dropId);
    }

    private static byte[] aesGcmDecrypt(byte[] key, byte[] nonce, byte[] ciphertext) {
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        AEADParameters params = new AEADParameters(new KeyParameter(key), GCM_TAG_BITS, nonce);
        cipher.init(false, params);

        byte[] output = new byte[cipher.getOutputSize(ciphertext.length)];
        int offset = cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
        try {
            cipher.doFinal(output, offset);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decryption failed", e);
        }
        return output;
    }

    // ── Getters ──────────────────────────────────────────────────────

    /**
     * Retorna el payload cifrado del RainMap (sin el nonce).
     *
     * @return copia del payload cifrado.
     */
    public byte[] getEncryptedPayload() {
        return encryptedPayload.clone();
    }

    /**
     * Retorna el payload combinado: nonce(12B) ‖ ciphertext.
     *
     * @return payload combinado, o {@code null} si el RainMap no está cifrado.
     */
    public byte[] getCombinedPayload() {
        if (nonce == null || encryptedPayload == null) return null;
        byte[] combined = new byte[NONCE_BYTES + encryptedPayload.length];
        System.arraycopy(nonce, 0, combined, 0, NONCE_BYTES);
        System.arraycopy(encryptedPayload, 0, combined, NONCE_BYTES, encryptedPayload.length);
        return combined;
    }

    /**
     * Retorna el nonce utilizado en el cifrado AES-GCM.
     *
     * @return copia del nonce (12 bytes).
     */
    public byte[] getNonce() {
        return nonce.clone();
    }

    /**
     * Retorna el número total de shares (parámetro n).
     *
     * @return cantidad total de drops distribuidos.
     */
    public int getN() {
        return n;
    }

    /**
     * Retorna el umbral mínimo de shares para reconstruir (parámetro k).
     *
     * @return umbral de reconstrucción.
     */
    public int getK() {
        return k;
    }

    /**
     * Retorna los commitments de Feldman VSS asociados a este RainMap.
     *
     * @return lista de commitments BigInteger, o {@code null} si no hay VSS.
     */
    public List<BigInteger> getCommitments() {
        return commitments;
    }

    /**
     * Indica si el RainMap tiene commitments de Feldman VSS.
     *
     * @return {@code true} si hay commitments válidos, {@code false} en caso contrario.
     */
    public boolean hasCommitments() {
        return commitments != null && !commitments.isEmpty();
    }
}
