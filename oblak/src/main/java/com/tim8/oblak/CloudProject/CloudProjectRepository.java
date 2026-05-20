package com.tim8.oblak.CloudProject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CloudProjectRepository extends JpaRepository<CloudProject, Long> {
    List<CloudProject> findByOwnerUsername(String ownerUsername);
}
