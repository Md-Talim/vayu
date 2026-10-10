package me.talim.vayu.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import me.talim.vayu.models.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {}
