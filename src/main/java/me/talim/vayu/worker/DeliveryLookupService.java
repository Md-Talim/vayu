package me.talim.vayu.worker;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.repositories.DeliveryRepository;

@Service
public class DeliveryLookupService {

    private final DeliveryRepository deliveryRepository;

    public DeliveryLookupService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryContext> load(ClaimedDelivery claim) {
        return deliveryRepository.findClaimedById(
                        claim.deliveryId(),
                        DeliveryStatus.DELIVERING,
                        claim.lockToken()
                )
                .map(this::toContext);
    }

    private DeliveryContext toContext(Delivery delivery) {
        return new DeliveryContext(
                delivery.getId(),
                delivery.getEvent().getId(),
                delivery.getEvent().getType(),
                delivery.getEvent().getPayload(),
                delivery.getSubscription().getEndpoint().getUrl(),
                delivery.getSubscription().getEndpoint().getSecret(),
                delivery.getSubscription().getEndpoint().isActive()
        );
    }
}
