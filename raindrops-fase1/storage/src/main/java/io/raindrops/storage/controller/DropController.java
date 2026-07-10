package io.raindrops.storage.controller;

import io.raindrops.storage.repository.DropRepository;
import io.raindrops.storage.service.DropService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DropController {

    private final DropService dropService;
    private final DropRepository dropRepository;
    private final String nodeId;

    public DropController(DropService dropService, DropRepository dropRepository) {
        this.dropService = dropService;
        this.dropRepository = dropRepository;
        this.nodeId = System.getenv().getOrDefault("NODE_ID", "storage-node-1");
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        long count = dropRepository.count();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "nodeId", nodeId,
                "dropsStored", count
        ));
    }
}
