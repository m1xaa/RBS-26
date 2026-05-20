package com.tim8.oblak.core.metadata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectMetadataRepository extends JpaRepository<ProjectMetadata, UUID> {
}
