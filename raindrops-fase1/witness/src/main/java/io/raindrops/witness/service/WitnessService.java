package io.raindrops.witness.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.core.Drop;
import io.raindrops.core.DropFactory;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class WitnessService {

    private static final Logger log = LoggerFactory.getLogger(WitnessService.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final List<String> storageUrls;

    public WitnessService(RestClient.Builder restClientBuilder,
                          @Value("${storage.urls:}") String storageUrlsRaw) {
        this.restClient = restClientBuilder.build();
        this.mapper = new ObjectMapper();
        this.storageUrls = parseUrls(storageUrlsRaw);
        log.info("Witness configured with storage nodes: {}", this.storageUrls);
    }

    public VerifyResult verifyDrop(String dropJson, String masterKeyHex) {
        try {
            Drop drop = DropSerializer.fromJson(dropJson);
            byte[] masterKey = HexFormat.of().parseHex(masterKeyHex);
            if (drop.isExpired()) {
                return new VerifyResult(false, "Drop expired", List.of());
            }
            DropFactory.verifyOrThrow(drop, masterKey);
            return new VerifyResult(true, "Drop valid", List.of(drop));
        } catch (DropFactory.InvalidDropException e) {
            return new VerifyResult(false, e.getMessage(), List.of());
        } catch (Exception e) {
            return new VerifyResult(false, "Verification error: " + e.getMessage(), List.of());
        }
    }

    public ReconstructResult reconstruct(String rainMapId, String masterKeyHex) {
        HexFormat hex = HexFormat.of();
        byte[] masterKey;
        try {
            masterKey = hex.parseHex(masterKeyHex);
        } catch (Exception e) {
            return new ReconstructResult(false, "Invalid masterKeyHex", null, List.of(), -1, -1);
        }

        if (storageUrls.isEmpty()) {
            return new ReconstructResult(false, "No storage nodes configured", null, List.of(), -1, -1);
        }

        String rainMapUrl = storageUrls.get(0) + "/rainmaps/" + rainMapId;
        String rainMapJson;
        try {
            rainMapJson = restClient.get().uri(URI.create(rainMapUrl)).retrieve().body(String.class);
        } catch (Exception e) {
            return new ReconstructResult(false, "Failed to fetch RainMap: " + e.getMessage(), null, List.of(), -1, -1);
        }

        if (rainMapJson == null) {
            return new ReconstructResult(false, "RainMap not found", null, List.of(), -1, -1);
        }

        Map<String, Object> rainMapData;
        try {
            rainMapData = mapper.readValue(rainMapJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new ReconstructResult(false, "Failed to parse RainMap JSON", null, List.of(), -1, -1);
        }

        String encryptedPayloadHex = (String) rainMapData.get("encryptedPayloadHex");
        log.debug("[DEBUG-RECON] masterKey hex: {}", "***");
        log.debug("[DEBUG-RECON] encryptedPayloadHex len: {}", encryptedPayloadHex != null ? encryptedPayloadHex.length() : 0);
        int n = (int) rainMapData.get("n");
        int k = (int) rainMapData.get("k");
        boolean directMode = rainMapData.containsKey("directMode") && (boolean) rainMapData.get("directMode");
        String ctHex = (String) rainMapData.get("ciphertextHex");
        byte[] ciphertext = ctHex != null ? hex.parseHex(ctHex) : null;

        byte[] combinedPayload = hex.parseHex(encryptedPayloadHex);
        log.debug("[DEBUG-RECON] combinedPayload len: {} hex first24: {}", combinedPayload.length, "***");
        RainMap rainMap;
        try {
            rainMap = RainMap.fromEncrypted(combinedPayload, masterKey);
        } catch (Exception e) {
            return new ReconstructResult(false, "Failed to unseal RainMap: " + e.getMessage(), null, List.of(), n, k);
        }

        // Extract VSS commitments from RainMap
        List<BigInteger> commitments = rainMap.getCommitments();

        Map<String, String> index = rainMap.unseal(masterKey);
        List<Drop> verifiedDrops = new ArrayList<>();
        List<String> badDrops = new ArrayList<>();

        for (Map.Entry<String, String> entry : index.entrySet()) {
            String dropId = entry.getKey();
            String nodeUrl = entry.getValue();

            try {
                String dropJson = restClient.get()
                    .uri(URI.create(nodeUrl + "/drops/" + dropId))
                    .retrieve()
                    .body(String.class);

                if (dropJson == null) {
                    badDrops.add(dropId + ": not found on " + nodeUrl);
                    continue;
                }

                Drop drop = DropSerializer.fromJson(dropJson);
                try {
                    DropFactory.verifyOrThrow(drop, masterKey);

                    // Verify VSS share against commitments
                    if (commitments != null && !commitments.isEmpty()) {
                        try {
                            io.raindrops.core.FeldmanVSS.verifyShareOrThrow(
                                drop.getX(), drop.getY(), commitments);
                        } catch (io.raindrops.core.FeldmanVSS.InvalidShareException e) {
                            badDrops.add(dropId + ": VSS verification failed - " + e.getMessage());
                            continue;
                        }
                    }

                    verifiedDrops.add(drop);
                } catch (DropFactory.InvalidDropException e) {
                    badDrops.add(dropId + ": " + e.getMessage());
                }
            } catch (Exception e) {
                badDrops.add(dropId + ": fetch error - " + e.getMessage());
            }

            if (verifiedDrops.size() >= k) break;
        }

        if (verifiedDrops.size() < k) {
            return new ReconstructResult(false,
                "Quorum insufficient: " + verifiedDrops.size() + "/" + k + " valid drops",
                null, badDrops, n, k);
        }

        try {
            byte[] data = RainDropsCore.reconstruct(verifiedDrops, masterKey, ciphertext, k, directMode, commitments);
            return new ReconstructResult(true, "Reconstruction successful", data, badDrops, n, k);
        } catch (Exception e) {
            return new ReconstructResult(false, "Reconstruction failed: " + e.getMessage(), null, badDrops, n, k);
        }
    }

    public StoreResult storeData(byte[] data, int n, int k, int ttlDays) {
        if (storageUrls.isEmpty()) {
            return new StoreResult(false, null, null);
        }

        try {
            RainDropsCore.RainResult result = RainDropsCore.drop(data, n, k, ttlDays);
            List<Drop> drops = result.getDrops();
            byte[] masterKey = result.getMasterKey();
            byte[] ciphertext = result.getCiphertext();
            List<BigInteger> commitments = result.getCommitments();

            List<String> dropJsons = drops.stream().map(DropSerializer::toJson).toList();
            List<String> usedUrls = new ArrayList<>();

            HexFormat hex = HexFormat.of();

            for (int i = 0; i < drops.size(); i++) {
                String url = storageUrls.get(i % storageUrls.size());
                usedUrls.add(url);
                restClient.post()
                    .uri(URI.create(url + "/drops"))
                    .body(dropJsons.get(i))
                    .retrieve()
                    .toBodilessEntity();
            }

            // Pass VSS commitments to RainMap
            RainMap rainMap = RainMap.create(drops, usedUrls, masterKey, k, commitments);
            String rainMapId = hex.formatHex(drops.get(0).getId());
            String payloadHex = hex.formatHex(rainMap.getCombinedPayload());
            log.debug("[DEBUG-STORE] payloadHex len: {} first40: {}", payloadHex.length(), "***");

            Map<String, Object> body = Map.of(
                "rainMapId", rainMapId,
                "encryptedPayloadHex", payloadHex,
                "n", rainMap.getN(),
                "k", rainMap.getK()
            );

            restClient.post()
                .uri(URI.create(usedUrls.get(0) + "/rainmaps/external"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body))
                .retrieve()
                .toBodilessEntity();
            log.debug("[DEBUG-STORE] POST to {}/rainmaps/external OK", usedUrls.get(0));

            if (ciphertext != null) {
                Map<String, Object> ctBody = Map.of(
                    "rainMapId", rainMapId,
                    "ciphertextHex", hex.formatHex(ciphertext)
                );
                restClient.put()
                    .uri(URI.create(usedUrls.get(0) + "/rainmaps/" + rainMapId + "/ciphertext"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(ctBody))
                    .retrieve()
                    .toBodilessEntity();
            }

            return new StoreResult(true, rainMapId, hex.formatHex(masterKey));
        } catch (Exception e) {
            return new StoreResult(false, "Store failed: " + e.getMessage(), null);
        }
    }

    private List<String> parseUrls(String raw) {
        List<String> urls = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String u : raw.split(",")) {
                u = u.trim();
                if (!u.isEmpty()) urls.add(u);
            }
        }
        return urls;
    }

    public record VerifyResult(boolean valid, String message, List<Drop> drops) {}
    public record ReconstructResult(boolean success, String message, byte[] data, List<String> badDrops, int n, int k) {}
    public record StoreResult(boolean success, String rainMapId, String masterKeyHex) {}
}
