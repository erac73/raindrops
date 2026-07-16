package io.raindrops.storage.controller;

import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.repository.DropRepository;
import io.raindrops.storage.service.DropService;
import io.raindrops.storage.service.RainMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DropController {

    private final DropService dropService;
    private final DropRepository dropRepository;
    private final RainMapService rainMapService;
    private final PeerConfig peerConfig;
    private final String nodeId;

    public DropController(DropService dropService, DropRepository dropRepository,
                          RainMapService rainMapService, PeerConfig peerConfig) {
        this.dropService = dropService;
        this.dropRepository = dropRepository;
        this.rainMapService = rainMapService;
        this.peerConfig = peerConfig;
        this.nodeId = peerConfig.getNodeId();
    }

    @PostMapping("/drops")
    public ResponseEntity<Map<String, String>> storeDrop(@RequestBody String body) {
        String dropId = dropService.storeDrop(body);
        return ResponseEntity.ok(Map.of("dropId", dropId, "nodeId", nodeId));
    }

    @GetMapping("/drops/{dropId}")
    public ResponseEntity<String> getDrop(@PathVariable String dropId) {
        String json = dropService.getDrop(dropId);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(json);
    }

    @PostMapping("/rainmaps")
    public ResponseEntity<Map<String, Object>> buildAndStoreRainMap(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> dropJsons = (List<String>) body.get("drops");
        @SuppressWarnings("unchecked")
        List<String> nodeUrls = (List<String>) body.get("nodeUrls");
        String masterKeyHex = (String) body.get("masterKeyHex");
        int k = ((Number) body.get("k")).intValue();

        java.util.HexFormat hex = java.util.HexFormat.of();
        byte[] masterKey = hex.parseHex(masterKeyHex);

        RainMapService.BuildRainMapResult result = RainMapService.buildRainMap(dropJsons, nodeUrls, masterKey, k);
        rainMapService.storeRainMap(result.rainMapId(), result.encryptedPayload(), result.n(), result.k(), null);

        return ResponseEntity.ok(Map.of(
            "rainMapId", result.rainMapId(),
            "encryptedPayloadHex", hex.formatHex(result.encryptedPayload()),
            "n", result.n(),
            "k", result.k()
        ));
    }

    @PostMapping("/rainmaps/external")
    public ResponseEntity<Map<String, String>> storeExternalRainMap(@RequestBody Map<String, Object> body) {
        String rainMapId = (String) body.get("rainMapId");
        String encryptedPayloadHex = (String) body.get("encryptedPayloadHex");
        int n = (int) body.get("n");
        int k = (int) body.get("k");

        java.util.HexFormat hex = java.util.HexFormat.of();
        rainMapService.storeRainMap(rainMapId, hex.parseHex(encryptedPayloadHex), n, k, null);

        return ResponseEntity.ok(Map.of("rainMapId", rainMapId));
    }

    @GetMapping("/rainmaps/{rainMapId}")
    public ResponseEntity<String> getRainMap(@PathVariable String rainMapId) {
        String json = rainMapService.getRainMap(rainMapId);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(json);
    }

    @PutMapping("/rainmaps/{rainMapId}/ciphertext")
    public ResponseEntity<Map<String, String>> storeCiphertext(
            @PathVariable String rainMapId, @RequestBody Map<String, Object> body) {
        String ciphertextHex = (String) body.get("ciphertextHex");
        rainMapService.storeCiphertext(rainMapId, ciphertextHex);
        return ResponseEntity.ok(Map.of("rainMapId", rainMapId));
    }

    @GetMapping("/peers")
    public ResponseEntity<Map<String, Object>> getPeers() {
        return ResponseEntity.ok(Map.of(
            "nodeId", nodeId,
            "peers", peerConfig.getPeerUrls()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        long count = dropRepository.count();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "nodeId", nodeId,
                "dropsStored", count,
                "peers", peerConfig.getPeerUrls()
        ));
    }
}