package me.talim.vayu.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.talim.vayu.dto.GetDeliveriesResponse;
import me.talim.vayu.dto.GetDeliveryResponse;
import me.talim.vayu.exception.ApiError;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.services.DeliveryService;

@RestController
@RequestMapping("/deliveries")
@Validated
@Tag(name = "Deliveries", description = "Inspect delivery status and attempt history")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a delivery", description = "Returns one delivery and its attempts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery returned"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Delivery not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<GetDeliveryResponse> getDelivery(
            @Parameter(description = "Delivery ID") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(deliveryService.getDelivery(id));
    }

    @GetMapping
    @Operation(summary = "List deliveries", description = "Returns a page of deliveries with optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deliveries returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid filter or pagination parameter",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<GetDeliveriesResponse> getDeliveries(
            @Parameter(description = "Filter by delivery status")
            @RequestParam(required = false) DeliveryStatus status,
            @Parameter(description = "Filter by endpoint ID")
            @RequestParam(required = false) UUID endpointId,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(deliveryService.getDeliveries(status, endpointId, page, size));
    }
}
