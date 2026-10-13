package me.talim.vayu.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import me.talim.vayu.models.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    long countByEvent_Id(UUID eventId);
}
