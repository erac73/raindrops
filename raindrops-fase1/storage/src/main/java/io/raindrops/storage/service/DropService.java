package io.raindrops.storage.service;

import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.storage.model.DropEntity;
import io.raindrops.storage.repository.DropRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class DropService {

    private final DropRepository dropRepository;
    private final String nodeId;

    public DropService(DropRepository dropRepository) {
        this.dropRepository = dropRepository;
        this.nodeId = System.getenv().getOrDefault("NODE_ID", "storage-node-1");
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

        return entity.getDropId();
    }

    @Transactional(readOnly = true)
    public String getDrop(String dropId) {
        Optional<DropEntity> opt = dropRepository.findByDropId(dropId);
        if (opt.isEmpty()) {
            return null;
        }

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

    @Transactional
    public void reapExpiredDrops() {
        long now = Instant.now().getEpochSecond();
        dropRepository.deleteByTtlBefore(now);
    }
}
