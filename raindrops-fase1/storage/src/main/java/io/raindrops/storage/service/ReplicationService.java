package io.raindrops.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.raindrops.storage.config.PeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final PeerConfig peerConfig;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String apiKey;

    public ReplicationService(PeerConfig peerConfig, RestClient.Builder restClientBuilder,
                              @Value("${API_KEY:}") String apiKey) {
        this.peerConfig = peerConfig;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restClient = restClientBuilder.requestFactory(factory).build();
        this.apiKey = apiKey;
    }

    private void addAuth(RestClient.RequestHeadersSpec<?> spec) {
        if (apiKey != null && !apiKey.isBlank()) {
            spec.header("X-API-Key", apiKey);
        }
    }

    public void replicateDrop(String dropId, String body) {
        for (String peer : peerConfig.getPeerUrls()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = peer + "/drops";
                    log.info("Replicating drop {} to {}", dropId, url);
                    var req = restClient.post().uri(url).body(body);
                    addAuth(req);
                    req.retrieve().toBodilessEntity();
                    log.info("Replicated drop {} to {}", dropId, url);
                } catch (Exception e) {
                    log.warn("Failed to replicate drop {} to peer {}: {}", dropId, peer, e.getMessage());
                }
            }, executor);
        }
    }

    public void replicateRainMap(String rainMapId, byte[] encryptedPayload, int n, int k, String ciphertextHex) {
        HexFormat hex = HexFormat.of();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rainMapId", rainMapId);
        body.put("encryptedPayloadHex", hex.formatHex(encryptedPayload));
        body.put("n", n);
        body.put("k", k);
        if (ciphertextHex != null) {
            body.put("ciphertextHex", ciphertextHex);
        }

        for (String peer : peerConfig.getPeerUrls()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = peer + "/rainmaps/external";
                    log.info("Replicating RainMap {} to {}", rainMapId, url);
                    var req = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(MAPPER.writeValueAsString(body));
                    addAuth(req);
                    req.retrieve().toBodilessEntity();
                    log.info("Replicated RainMap {} to {}", rainMapId, url);
                } catch (Exception e) {
                    log.warn("Failed to replicate RainMap {} to peer {}: {}", rainMapId, peer, e.getMessage());
                }
            }, executor);
        }
    }

    public String tryFetchFromPeers(String dropId) {
        for (String peer : peerConfig.getPeerUrls()) {
            try {
                String url = peer + "/drops/" + dropId;
                log.info("Fetching drop {} from peer {}", dropId, url);
                var req = restClient.get().uri(url);
                addAuth(req);
                String body = req.retrieve().body(String.class);
                if (body != null && !body.isBlank()) {
                    log.info("Found drop {} on peer {}", dropId, url);
                    return body;
                }
            } catch (Exception e) {
                log.debug("Drop {} not on peer {}: {}", dropId, peer, e.getMessage());
            }
        }
        return null;
    }
}