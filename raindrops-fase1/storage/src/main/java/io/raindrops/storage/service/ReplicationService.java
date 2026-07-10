package io.raindrops.storage.service;

import io.raindrops.storage.config.PeerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final PeerConfig peerConfig;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ReplicationService(PeerConfig peerConfig, RestClient.Builder restClientBuilder) {
        this.peerConfig = peerConfig;
        this.restClient = restClientBuilder.build();
    }

    public void replicateDrop(String dropId, String body) {
        for (String peer : peerConfig.getPeerUrls()) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = peer + "/drops";
                    log.info("Replicating drop {} to {}", dropId, url);
                    restClient.post()
                        .uri(url)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                    log.info("Replicated drop {} to {}", dropId, url);
                } catch (Exception e) {
                    log.warn("Failed to replicate drop {} to peer {}: {}", dropId, peer, e.getMessage());
                }
            }, executor);
        }
    }

    public String tryFetchFromPeers(String dropId) {
        for (String peer : peerConfig.getPeerUrls()) {
            try {
                String url = peer + "/drops/" + dropId;
                log.info("Fetching drop {} from peer {}", dropId, url);
                String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
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