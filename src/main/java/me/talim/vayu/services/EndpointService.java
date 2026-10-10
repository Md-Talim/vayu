package me.talim.vayu.services;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.repositories.EndpointRepository;
import me.talim.vayu.repositories.SubscriptionRepository;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SecureRandom secureRandom;

    public EndpointService(
        EndpointRepository endpointRepository,
        SubscriptionRepository subscriptionRepository,
        SecureRandom secureRandom
    ) {
        this.endpointRepository = endpointRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public CreateEndpointResponse createEndpoint(CreateEndpointRequest request) {
        Endpoint endpoint = endpointRepository.save(new Endpoint(request.url(), generateSecret()));

        List<Subscription> subscriptions = request
            .eventTypes()
            .stream()
            .map(type -> new Subscription(endpoint, type))
            .toList();
        subscriptionRepository.saveAll(subscriptions);

        return CreateEndpointResponse.from(endpoint, request.eventTypes());
    }

    private String generateSecret() {
        byte[] secretBytes = new byte[32];

        secureRandom.nextBytes(secretBytes);

        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }
}
