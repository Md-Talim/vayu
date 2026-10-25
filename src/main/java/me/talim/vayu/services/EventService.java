package me.talim.vayu.services;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.exception.IdempotencyConflictException;
import me.talim.vayu.exception.InvalidEventPayloadException;
import me.talim.vayu.models.Event;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EventRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final EventWriter eventWriter;

    public EventService(
            EventRepository eventRepository,
            DeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            EventWriter eventWriter
    ) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
        this.eventWriter = eventWriter;
    }

    public CreateEventResult createEvent(CreateEventRequest request, String idempotencyKey) {
        JsonNode payload = parsePayload(request.payload());

        Optional<Event> existingEvent = eventRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEvent.isPresent()) {
            return replay(existingEvent.get(), request, payload);
        }

        try {
            return eventWriter.createEvent(request, idempotencyKey);
        } catch (DataIntegrityViolationException exception) {
            return replayAfterConcurrentInsert(exception, request, idempotencyKey, payload);
        }
    }

    private CreateEventResult replayAfterConcurrentInsert(
            DataIntegrityViolationException exception,
            CreateEventRequest request,
            String idempotencyKey,
            JsonNode payload
    ) {
        Event existingEvent = eventRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> exception);

        return replay(existingEvent, request, payload);
    }

    private CreateEventResult replay(Event event, CreateEventRequest request, JsonNode payload) {
        if (!isSameRequest(event, request, payload)) {
            throw new IdempotencyConflictException(
                    "Idempotency key already used for a different event"
            );
        }

        long deliveryCount = deliveryRepository.countByEvent_Id(event.getId());

        return new CreateEventResult(
                CreateEventResponse.from(event, (int) deliveryCount),
                true
        );
    }

    private boolean isSameRequest(
            Event event,
            CreateEventRequest request,
            JsonNode requestedPayload
    ) {
        if (!event.getType().equals(request.type())) {
            return false;
        }

        return parsePayload(event.getPayload()).equals(requestedPayload);
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventPayloadException("Event payload must be valid JSON");
        }

        try {
            JsonNode parsed = objectMapper.readTree(payload);

            if (parsed == null || parsed.isMissingNode()) {
                throw new InvalidEventPayloadException("Event payload must be valid JSON");
            }

            return parsed;
        } catch (JacksonException exception) {
            throw new InvalidEventPayloadException("Event payload must be valid JSON", exception);
        }
    }
}
