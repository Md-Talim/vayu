package me.talim.vayu.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.exception.IdempotencyConflictException;
import me.talim.vayu.exception.InvalidEventPayloadException;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.Event;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EventRepository;
import me.talim.vayu.repositories.SubscriptionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public EventService(
            EventRepository eventRepository,
            SubscriptionRepository subscriptionRepository,
            DeliveryRepository deliveryRepository,
            ObjectMapper objectMapper
    ) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateEventResult createEvent(CreateEventRequest request, String idempotencyKey) {
        var existingEvent = eventRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEvent.isPresent()) {
            Event event = existingEvent.get();

            if (!isSameRequest(event, request)) {
                throw new IdempotencyConflictException("Idempotency key already used for a different event");
            }

            long existingDeliveryCount = deliveryRepository.countByEvent_Id(event.getId());

            return new CreateEventResult(CreateEventResponse.from(event, (int) existingDeliveryCount), true);
        }

        Event event = new Event(request.type(), request.payload(), idempotencyKey);

        List<Subscription> subscriptions = subscriptionRepository.findAllByEventType(request.type());
        List<Delivery> deliveries = subscriptions
                .stream()
                .map(subscription -> new Delivery(event, subscription))
                .collect(Collectors.toList());

        Event savedEvent = eventRepository.save(event);
        deliveryRepository.saveAll(deliveries);

        return new CreateEventResult(CreateEventResponse.from(savedEvent, deliveries.size()), false);
    }

    private boolean isSameRequest(
            Event existingEvent,
            CreateEventRequest request
    ) {
        if (!existingEvent.getType().equals(request.type())) {
            return false;
        }

        try {
            JsonNode existingPayload
                    = objectMapper.readTree(existingEvent.getPayload());

            JsonNode requestedPayload
                    = objectMapper.readTree(request.payload());

            return existingPayload.equals(requestedPayload);
        } catch (JacksonException exception) {
            throw new InvalidEventPayloadException(
                    "Event payload must be valid JSON",
                    exception
            );
        }
    }
}
