package io.raindrops.witness.controller;

import io.raindrops.witness.service.WitnessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
public class WitnessController {

    private final WitnessService witnessService;

    public WitnessController(WitnessService witnessService) {
        this.witnessService = witnessService;
    }

    @PostMapping("/witness/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        String dropJson = body.get("dropJson");
        String masterKeyHex = body.get("masterKeyHex");
        if (dropJson == null || masterKeyHex == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "dropJson and masterKeyHex required"));
        }
        WitnessService.VerifyResult result = witnessService.verifyDrop(dropJson, masterKeyHex);
        return ResponseEntity.ok(Map.of(
            "valid", result.valid(),
            "message", result.message()
        ));
    }

    @PostMapping("/witness/store")
    public ResponseEntity<Map<String, Object>> store(@RequestBody Map<String, Object> body) {
        String dataBase64 = (String) body.get("data");
        int n = (int) body.get("n");
        int k = (int) body.get("k");
        int ttlDays = (int) body.get("ttlDays");

        if (dataBase64 == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "data (base64) required"));
        }

        byte[] data = Base64.getDecoder().decode(dataBase64);
        WitnessService.StoreResult result = witnessService.storeData(data, n, k, ttlDays);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Store failed"));
        }

        return ResponseEntity.ok(Map.of(
            "rainMapId", result.rainMapId(),
            "masterKeyHex", result.masterKeyHex()
        ));
    }

    @PostMapping("/witness/reconstruct")
    public ResponseEntity<Map<String, Object>> reconstruct(@RequestBody Map<String, String> body) {
        String rainMapId = body.get("rainMapId");
        String masterKeyHex = body.get("masterKeyHex");
        if (rainMapId == null || masterKeyHex == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rainMapId and masterKeyHex required"));
        }

        WitnessService.ReconstructResult result = witnessService.reconstruct(rainMapId, masterKeyHex);

        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", result.message(),
                "badDrops", result.badDrops(),
                "n", result.n(),
                "k", result.k()
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Base64.getEncoder().encodeToString(result.data()),
            "badDrops", result.badDrops(),
            "n", result.n(),
            "k", result.k()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "witness"));
    }
}
