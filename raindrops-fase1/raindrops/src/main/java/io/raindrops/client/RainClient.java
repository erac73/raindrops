package io.raindrops.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class RainClient implements AutoCloseable {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final List<String> nodeUrls;
    private final HexFormat hex;

    public RainClient(List<String> nodeUrls) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.nodeUrls = new ArrayList<>(nodeUrls);
        this.hex = HexFormat.of();
    }

    public String store(byte[] data, int n, int k, int ttlDays) throws Exception {
        RainDropsCore.RainResult result = RainDropsCore.drop(data, n, k, ttlDays);
        List<Drop> drops = result.getDrops();
        byte[] masterKey = result.getMasterKey();
        byte[] ciphertext = result.getCiphertext();

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

        RainMap rainMap = RainMap.create(drops, usedUrls, masterKey);
        String rainMapId = hex.formatHex(drops.get(0).getId());
        String payloadHex = hex.formatHex(rainMap.getEncryptedPayload());

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
                drops.add(drop);
                if (drops.size() >= k) break;
            }
        }

        if (drops.size() < k) {
            throw new RuntimeException("Not enough shares: " + drops.size() + "/" + k);
        }

        return RainDropsCore.reconstruct(drops, masterKey, ciphertext, k, directMode);
    }

    @Override
    public void close() {
    }
}