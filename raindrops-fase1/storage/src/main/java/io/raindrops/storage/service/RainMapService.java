package io.raindrops.storage.service;

import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainMap;
import io.raindrops.storage.config.PeerConfig;
import io.raindrops.storage.model.RainMapEntity;
import io.raindrops.storage.repository.RainMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class RainMapService {

    private static final Logger log = LoggerFactory.getLogger(RainMapService.class);

    private final RainMapRepository rainMapRepository;
    private final ReplicationService replicationService;
    private final String nodeId;

    public RainMapService(RainMapRepository rainMapRepository,
                          ReplicationService replicationService,
                          @Value("${NODE_ID:storage-node}") String nodeId) {
        this.rainMapRepository = rainMapRepository;
        this.replicationService = replicationService;
        this.nodeId = nodeId;
    }

    @Transactional
    public String storeRainMap(String rainMapId, byte[] encryptedPayload, int n, int k, String ciphertextHex) {
        HexFormat hex = HexFormat.of();
        RainMapEntity entity = rainMapRepository.findByRainMapId(rainMapId).orElse(new RainMapEntity());
        entity.setRainMapId(rainMapId);
        entity.setEncryptedPayloadHex(hex.formatHex(encryptedPayload));
        entity.setN(n);
        entity.setK(k);
        entity.setNodeId(nodeId);
        if (ciphertextHex != null) {
            entity.setCiphertextHex(ciphertextHex);
        }
        entity.setCreatedAt(LocalDateTime.now());
        rainMapRepository.save(entity);
        log.info("Stored RainMap {} (n={}, k={})", rainMapId, n, k);

        replicationService.replicateRainMap(rainMapId, encryptedPayload, n, k, ciphertextHex);

        return rainMapId;
    }

    @Transactional
    public void storeCiphertext(String rainMapId, String ciphertextHex) {
        rainMapRepository.findByRainMapId(rainMapId).ifPresent(entity -> {
            entity.setCiphertextHex(ciphertextHex);
            rainMapRepository.save(entity);
            log.info("Stored ciphertext for RainMap {}", rainMapId);
        });
    }

    @Transactional(readOnly = true)
    public String getRainMap(String rainMapId) {
        Optional<RainMapEntity> opt = rainMapRepository.findByRainMapId(rainMapId);
        if (opt.isEmpty()) {
            return null;
        }
        RainMapEntity entity = opt.get();
        boolean hasCiphertext = entity.getCiphertextHex() != null && !entity.getCiphertextHex().isBlank();
        return "{\"rainMapId\":\"" + entity.getRainMapId()
            + "\",\"encryptedPayloadHex\":\"" + entity.getEncryptedPayloadHex()
            + "\",\"n\":" + entity.getN()
            + ",\"k\":" + entity.getK()
            + ",\"directMode\":" + !hasCiphertext
            + ",\"ciphertextHex\":" + (hasCiphertext ? "\"" + entity.getCiphertextHex() + "\"" : "null")
            + ",\"nodeId\":\"" + entity.getNodeId()
            + "\"}";
    }

    public static BuildRainMapResult buildRainMap(List<String> dropJsons, List<String> nodeUrls, byte[] masterKey) {
        HexFormat hex = HexFormat.of();
        List<Drop> drops = dropJsons.stream().map(DropSerializer::fromJson).toList();
        RainMap rainMap = RainMap.create(drops, nodeUrls, masterKey);
        String rainMapId = hex.formatHex(drops.get(0).getId());
        return new BuildRainMapResult(rainMapId, rainMap.getEncryptedPayload(), rainMap.getN(), rainMap.getK());
    }

    public record BuildRainMapResult(String rainMapId, byte[] encryptedPayload, int n, int k) {}
}