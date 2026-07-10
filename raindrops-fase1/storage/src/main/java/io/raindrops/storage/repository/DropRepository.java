package io.raindrops.storage.repository;

import io.raindrops.storage.model.DropEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DropRepository extends JpaRepository<DropEntity, Long> {

    Optional<DropEntity> findByDropId(String dropId);

    void deleteByTtlBefore(long timestamp);
}
