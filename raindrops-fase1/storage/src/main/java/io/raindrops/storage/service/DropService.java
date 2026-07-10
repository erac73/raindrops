package io.raindrops.storage.service;

import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.storage.model.DropEntity;
import io.raindrops.storage.repository.DropRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class DropService {

    private static final Logger log = LoggerFactory.getLogger(DropService.class);

    private final DropRepository dropRepository;
    private final ReplicationService replicationService;
    private final String nodeId;

    public DropService(DropRepository dropRepository,
                       ReplicationService replicationService,
                       @org.springframework.beans.factory.annotation.Value("${NODE_ID:storage-node}") String nodeId) {
        this.dropRepository = dropRepository;
        this.replicationService = replicationService;
        this.nodeId = nodeId;
    }

    @Transactional
    public String storeDrop(String dropJson) {
        Drop drop = DropSerializer.fromJson(dropJson);
        HexFormat hex = HexFormat.of();

        DropEntity entity = new DropEntity();
        entity.setDropId(hex.formatHex(drop.getId()));
        entity.setX(drop.getX());
        entity.setY(drop.getY().toString(16));
        entity.setMac(hex.formatHex(drop.getMac()));
        entity.setTtl(drop.getTtl());
        entity.setNodeId(nodeId);
        entity.setCreatedAt(LocalDateTime.now());

        dropRepository.save(entity);

        String dropId = entity.getDropId();
        log.info("Stored drop {} on node {}", dropId, nodeId);

        replicationService.replicateDrop(dropId, dropJson);

        return dropId;
    }

    @Transactional(readOnly = true)
    public String getDrop(String dropId) {
        Optional<DropEntity> opt = dropRepository.findByDropId(dropId);
        if (opt.isPresent()) {
            DropEntity entity = opt.get();
            HexFormat hex = HexFormat.of();

            Drop drop = new Drop(
                hex.parseHex(entity.getDropId()),
                entity.getX(),
                new java.math.BigInteger(entity.getY(), 16),
                hex.parseHex(entity.getMac()),
                entity.getTtl()
            );
            return DropSerializer.toJson(drop);
        }

        String fromPeer = replicationService.tryFetchFromPeers(dropId);
        if (fromPeer != null) {
            log.info("Retrieved drop {} from peer", dropId);
            return fromPeer;
        }

        return null;
    }

    @Transactional
    public void reapExpiredDrops() {
        long now = Instant.now().getEpochSecond();
        dropRepository.deleteByTtlBefore(now);
        log.debug("Reaper run completed");
    }
}