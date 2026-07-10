package io.raindrops.storage.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PeerConfig {

    private static final Logger log = LoggerFactory.getLogger(PeerConfig.class);

    private final String peerUrlsRaw;
    private List<String> peerUrls = Collections.emptyList();

    private final String nodeId;
    private final int serverPort;

    public PeerConfig(
            @Value("${peers.urls:}") String peerUrlsRaw,
            @Value("${NODE_ID:storage-node}") String nodeId,
            @Value("${server.port:8080}") int serverPort) {
        this.peerUrlsRaw = peerUrlsRaw;
        this.nodeId = nodeId;
        this.serverPort = serverPort;
    }

    @PostConstruct
    public void init() {
        List<String> urls = new ArrayList<>();
        if (peerUrlsRaw != null && !peerUrlsRaw.isBlank()) {
            for (String u : peerUrlsRaw.split(",")) {
                u = u.trim();
                if (!u.isEmpty()) {
                    urls.add(u);
                }
            }
        }
        this.peerUrls = Collections.unmodifiableList(urls);
        log.info("Node {} peers: {}", nodeId, peerUrls);
    }

    public List<String> getPeerUrls() {
        return peerUrls;
    }

    public String getMyUrl() {
        return "http://" + nodeId + ":" + serverPort;
    }

    public String getNodeId() {
        return nodeId;
    }
}