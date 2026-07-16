package io.raindrops.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.FeldmanVSS;
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainMap;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para interactuar con el sistema RainDrops.
 *
 * <p>Proporciona una interfaz sencilla para almacenar y recuperar datos
 * distribuidos a través de los nodos Storage. Gestiona internamente la
 * división de datos en drops, la creación del RainMap y la reconstrucción.</p>
 *
 * <p>Implementa {@link AutoCloseable} para gestión adecuada de recursos.
 * Se recomienda usar con try-with-resources:</p>
 * <pre>{@code
 * try (RainClient client = new RainClient(nodeUrls)) {
 *     String rainMapId = client.store(data, 5, 3, 30);
 *     byte[] recovered = client.retrieve(rainMapId, masterKey);
 * }
 * }</pre>
 *
 * <p>Este cliente no es thread-safe. Cada instancia debe ser usada por un solo hilo
 * a la vez, o sincronizarse externamente.</p>
 */
public final class RainClient implements AutoCloseable {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final List<String> nodeUrls;
    private final HexFormat hex;

    /**
     * Crea un nuevo RainClient con la lista de nodos Storage.
     *
     * @param nodeUrls lista de URLs de los nodos Storage (al menos uno requerido).
     */
    public RainClient(List<String> nodeUrls) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.nodeUrls = new ArrayList<>(nodeUrls);
        this.hex = HexFormat.of();
    }

    /**
     * Almacena datos distribuyéndolos entre los nodos Storage.
     *
     * <p>Divide los datos en n drops usando Shamir Secret Sharing, los distribuye
     * entre los nodos, genera el RainMap con VSS commitments y lo almacena en
     * el primer nodo.</p>
     *
     * @param data     datos originales a proteger.
     * @param n        número total de shares a generar.
     * @param k        umbral mínimo de shares para reconstruir.
     * @param ttlDays  tiempo de vida de los drops en días.
     * @return identificador del RainMap creado (hexadecimal).
     * @throws Exception si ocurre un error durante la comunicación HTTP o el procesamiento.
     */
    public String store(byte[] data, int n, int k, int ttlDays) throws Exception {
        RainDropsCore.RainResult result = RainDropsCore.drop(data, n, k, ttlDays);
        List<Drop> drops = result.getDrops();
        byte[] masterKey = result.getMasterKey();
        byte[] ciphertext = result.getCiphertext();
        List<BigInteger> commitments = result.getCommitments();

        List<String> dropJsons = drops.stream().map(DropSerializer::toJson).toList();
        List<String> usedUrls = new ArrayList<>();

        for (int i = 0; i < drops.size(); i++) {
            String url = nodeUrls.get(i % nodeUrls.size());
            usedUrls.add(url);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/drops"))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(dropJsons.get(i)))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        }

        // Pass VSS commitments to RainMap
        RainMap rainMap = RainMap.create(drops, usedUrls, masterKey, k, commitments);
        String rainMapId = hex.formatHex(drops.get(0).getId());
        String payloadHex = hex.formatHex(rainMap.getCombinedPayload());

        String firstUrl = nodeUrls.get(0);
        Map<String, Object> body = Map.of(
            "rainMapId", rainMapId,
            "encryptedPayloadHex", payloadHex,
            "n", rainMap.getN(),
            "k", rainMap.getK()
        );
        HttpRequest rainReq = HttpRequest.newBuilder()
                .uri(URI.create(firstUrl + "/rainmaps/external"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(15))
                .build();
        http.send(rainReq, HttpResponse.BodyHandlers.ofString());

        if (ciphertext != null) {
            Map<String, Object> ctBody = Map.of(
                "rainMapId", rainMapId,
                "ciphertextHex", hex.formatHex(ciphertext)
            );
            HttpRequest ctReq = HttpRequest.newBuilder()
                    .uri(URI.create(firstUrl + "/rainmaps/" + rainMapId + "/ciphertext"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(ctBody)))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            http.send(ctReq, HttpResponse.BodyHandlers.ofString());
        }

        return rainMapId;
    }

    /**
     * Recupera y reconstruye los datos originales a partir de un RainMap.
     *
     * <p>Obtiene el RainMap del primer nodo, descifra el índice de drops,
     * recupera cada drop verificando VSS si hay commitments, y reconstruye
     * el dato original con el umbral k.</p>
     *
     * @param rainMapId identificador del RainMap a recuperar (hexadecimal).
     * @param masterKey clave maestra para descifrar el RainMap (32 bytes).
     * @return datos originales reconstruidos.
     * @throws RuntimeException si no se encuentra el RainMap o no hay suficientes shares válidos.
     * @throws Exception         si ocurre un error durante la comunicación HTTP.
     */
    public byte[] retrieve(String rainMapId, byte[] masterKey) throws Exception {
        String rainMapUrl = nodeUrls.get(0) + "/rainmaps/" + rainMapId;
        HttpRequest rainReq = HttpRequest.newBuilder()
                .uri(URI.create(rainMapUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> rainResp = http.send(rainReq, HttpResponse.BodyHandlers.ofString());
        if (rainResp.statusCode() != 200) {
            throw new RuntimeException("RainMap not found: " + rainMapId);
        }

        Map<String, Object> rainMapData = mapper.readValue(rainResp.body(),
                new TypeReference<Map<String, Object>>() {});
        byte[] encryptedPayload = hex.parseHex((String) rainMapData.get("encryptedPayloadHex"));
        int n = (int) rainMapData.get("n");
        int k = (int) rainMapData.get("k");
        boolean directMode = rainMapData.containsKey("directMode") && (boolean) rainMapData.get("directMode");

        String ctHex = (String) rainMapData.get("ciphertextHex");
        byte[] ciphertext = ctHex != null ? hex.parseHex(ctHex) : null;

        RainMap rainMap = RainMap.fromEncrypted(encryptedPayload, masterKey);

        // Extract VSS commitments from RainMap
        List<BigInteger> commitments = rainMap.getCommitments();

        Map<String, String> index = rainMap.unseal(masterKey);

        List<Drop> drops = new ArrayList<>();
        for (Map.Entry<String, String> entry : index.entrySet()) {
            String dropId = entry.getKey();
            String nodeUrl = entry.getValue();
            HttpRequest dropReq = HttpRequest.newBuilder()
                    .uri(URI.create(nodeUrl + "/drops/" + dropId))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> dropResp = http.send(dropReq, HttpResponse.BodyHandlers.ofString());
            if (dropResp.statusCode() == 200) {
                Drop drop = DropSerializer.fromJson(dropResp.body());

                // Verify VSS share against commitments
                if (commitments != null && !commitments.isEmpty()) {
                    try {
                        FeldmanVSS.verifyShareOrThrow(drop.getX(), drop.getY(), commitments);
                    } catch (FeldmanVSS.InvalidShareException e) {
                        // Skip invalid shares
                        continue;
                    }
                }

                drops.add(drop);
                if (drops.size() >= k) break;
            }
        }

        if (drops.size() < k) {
            throw new RuntimeException("Not enough valid shares: " + drops.size() + "/" + k);
        }

        return RainDropsCore.reconstruct(drops, masterKey, ciphertext, k, directMode, commitments);
    }

    /**
     * Cierra el cliente y libera los recursos HTTP asociados.
     */
    @Override
    public void close() {
    }
}
