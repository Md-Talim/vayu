package me.talim.vayu.services;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.models.Endpoint;
import me.talim.vayu.repositories.EndpointRepository;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final SecureRandom secureRandom;

    public EndpointService(EndpointRepository endpointRepository, SecureRandom secureRandom) {
        this.endpointRepository = endpointRepository;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public CreateEndpointResponse createEndpoint(CreateEndpointRequest request) {
        String secret = generateSecret();

        Endpoint endpoint = new Endpoint(request.url(), secret);

        Endpoint savedEndpoint = endpointRepository.save(endpoint);

        return CreateEndpointResponse.from(savedEndpoint);
    }

    private String generateSecret() {
        byte[] secretBytes = new byte[32];

        secureRandom.nextBytes(secretBytes);

        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }
}
