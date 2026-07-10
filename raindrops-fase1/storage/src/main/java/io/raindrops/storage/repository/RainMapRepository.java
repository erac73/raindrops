package io.raindrops.storage.repository;

import io.raindrops.storage.model.RainMapEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RainMapRepository extends JpaRepository<RainMapEntity, String> {

    Optional<RainMapEntity> findByRainMapId(String rainMapId);
}