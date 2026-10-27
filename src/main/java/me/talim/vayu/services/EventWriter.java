package me.talim.vayu.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.Event;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EventRepository;
import me.talim.vayu.repositories.SubscriptionRepository;

/**
 * Persists the event and its deliveries in a separate transaction so that a
 * duplicate idempotency key from a concurrent request can be handled after
 * rollback.
 */
@Service
public class EventWriter {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryRepository deliveryRepository;

    public EventWriter(
            EventRepository eventRepository,
            SubscriptionRepository subscriptionRepository,
            DeliveryRepository deliveryRepository
    ) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public CreateEventResult createEvent(CreateEventRequest request, String idempotencyKey) {
        Event event = new Event(request.type(), request.payload(), idempotencyKey);

        List<Subscription> subscriptions = subscriptionRepository
                .findAllByEventTypeAndEndpoint_ActiveTrue(request.type());
        List<Delivery> deliveries = subscriptions
                .stream()
                .map(subscription -> new Delivery(event, subscription))
                .toList();

        Event savedEvent = eventRepository.save(event);
        deliveryRepository.saveAll(deliveries);

        return new CreateEventResult(
                CreateEventResponse.from(savedEvent, deliveries.size()),
                false
        );
    }
}
