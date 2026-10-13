package me.talim.vayu.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import me.talim.vayu.models.Endpoint;

public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {

    Page<Endpoint> findByActive(boolean active, Pageable pageable);
}
