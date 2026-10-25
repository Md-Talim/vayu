package me.talim.vayu.services;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.DeliverySummaryResponse;
import me.talim.vayu.dto.GetDeliveriesResponse;
import me.talim.vayu.dto.GetDeliveryResponse;
import me.talim.vayu.exception.NotFoundException;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.repositories.DeliveryRepository;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public GetDeliveryResponse getDelivery(UUID id) {
        Delivery delivery = deliveryRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("delivery " + id + " not found"));

        return GetDeliveryResponse.from(delivery);
    }

    @Transactional(readOnly = true)
    public GetDeliveriesResponse getDeliveries(
            DeliveryStatus status,
            UUID endpointId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Delivery> deliveryPage = deliveryRepository.findPage(status, endpointId, pageable);

        List<DeliverySummaryResponse> items = deliveryPage.getContent()
                .stream()
                .map(DeliverySummaryResponse::from)
                .toList();

        return new GetDeliveriesResponse(
                items,
                deliveryPage.getNumber(),
                deliveryPage.getSize(),
                deliveryPage.getTotalElements(),
                deliveryPage.getTotalPages()
        );
    }
}
