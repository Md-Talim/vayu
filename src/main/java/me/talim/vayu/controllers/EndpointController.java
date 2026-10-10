package me.talim.vayu.controllers;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.services.EndpointService;

@RestController
@RequestMapping("/endpoints")
@Validated
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    public ResponseEntity<CreateEndpointResponse> createEndpoint(
        @Valid @RequestBody CreateEndpointRequest request
    ) {
        CreateEndpointResponse response = endpointService.createEndpoint(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }
}
