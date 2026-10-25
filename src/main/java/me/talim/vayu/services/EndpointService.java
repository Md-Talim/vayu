package me.talim.vayu.services;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.dto.GetEndpointResponse;
import me.talim.vayu.dto.GetEndpointsResponse;
import me.talim.vayu.exception.NotFoundException;
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

        List<Subscription> subscriptions = request.eventTypes().stream()
                .map(type -> new Subscription(endpoint, type))
                .toList();
        subscriptionRepository.saveAll(subscriptions);

        return CreateEndpointResponse.from(endpoint, request.eventTypes());
    }

    public GetEndpointResponse getEndpoint(UUID id) {
        Endpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("endpoint " + id + " not found"));

        List<Subscription> subscriptions = subscriptionRepository.findAllByEndpoint(endpoint);
        var eventTypes = subscriptions
                .stream()
                .map(subscription -> subscription.getEventType())
                .collect(Collectors.toSet());

        return GetEndpointResponse.from(endpoint, eventTypes);
    }

    public GetEndpointsResponse getAllEndpoints(Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Endpoint> endpointPage = active == null
                ? endpointRepository.findAll(pageable)
                : endpointRepository.findByActive(active, pageable);

        List<Endpoint> endpoints = endpointPage.getContent();

        Map<UUID, Set<String>> eventTypesByEndpoint;

        if (endpoints.isEmpty()) {
            eventTypesByEndpoint = Map.of();
        } else {
            List<UUID> endpointIds = endpoints.stream().map(Endpoint::getId).toList();
            List<Subscription> subscriptions = subscriptionRepository.findAllByEndpoint_IdIn(endpointIds);

            eventTypesByEndpoint = subscriptions.stream()
                    .collect(Collectors.groupingBy(
                            subscription -> subscription.getEndpoint().getId(),
                            Collectors.mapping(Subscription::getEventType, Collectors.toSet())
                    ));
        }

        List<GetEndpointResponse> items = endpoints.stream()
                .map(endpoint -> GetEndpointResponse.from(
                        endpoint,
                        eventTypesByEndpoint.getOrDefault(endpoint.getId(), Set.of())
                )).toList();

        return new GetEndpointsResponse(
                items,
                endpointPage.getNumber(),
                endpointPage.getSize(),
                endpointPage.getTotalElements(),
                endpointPage.getTotalPages()
        );
    }

    private String generateSecret() {
        byte[] secretBytes = new byte[32];

        secureRandom.nextBytes(secretBytes);

        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }
}
