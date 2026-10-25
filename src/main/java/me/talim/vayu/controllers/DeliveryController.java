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
import me.talim.vayu.dto.GetDeliveriesResponse;
import me.talim.vayu.dto.GetDeliveryResponse;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.services.DeliveryService;

@RestController
@RequestMapping("/deliveries")
@Validated
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetDeliveryResponse> getDelivery(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.getDelivery(id));
    }

    @GetMapping
    public ResponseEntity<GetDeliveriesResponse> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(deliveryService.getDeliveries(status, endpointId, page, size));
    }
}
