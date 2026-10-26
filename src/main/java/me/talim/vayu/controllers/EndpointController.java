package me.talim.vayu.controllers;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.dto.GetEndpointResponse;
import me.talim.vayu.dto.GetEndpointsResponse;
import me.talim.vayu.exception.ApiError;
import me.talim.vayu.services.EndpointService;

@RestController
@RequestMapping("/endpoints")
@Validated
@Tag(name = "Endpoints", description = "Manage webhook endpoints and event subscriptions")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @Operation(
            summary = "Create an endpoint",
            description = "Creates a webhook endpoint, subscribes it to event types, and returns its signing secret"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Endpoint created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid URL, event types, or request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
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

    @GetMapping
    @Operation(summary = "List endpoints", description = "Returns a page of endpoints")
    @ApiResponse(responseCode = "200", description = "Endpoints returned")
    public ResponseEntity<GetEndpointsResponse> getAllEndpoints(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        GetEndpointsResponse response = endpointService.getAllEndpoints(active, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an endpoint", description = "Returns one endpoint without its signing secret")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endpoint returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Endpoint not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<GetEndpointResponse> getEndpointById(@PathVariable UUID id) {
        return ResponseEntity.ok(endpointService.getEndpoint(id));
    }
}
