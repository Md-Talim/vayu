package me.talim.vayu.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.exception.IdempotencyConflictException;
import me.talim.vayu.exception.InvalidEventPayloadException;
import me.talim.vayu.models.Event;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EventRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private EventWriter eventWriter;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        eventService = new EventService(
                eventRepository,
                deliveryRepository,
                objectMapper,
                eventWriter
        );
    }

    @Test
    void rejectsInvalidPayloadBeforeAnyDatabaseLookup() {
        CreateEventRequest request = new CreateEventRequest("payment.succeeded", "{not-json");

        assertThatThrownBy(() -> eventService.createEvent(request, "idem-1"))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessage("Event payload must be valid JSON");

        verifyNoInteractions(eventRepository, deliveryRepository, eventWriter);
    }

    @Test
    void rejectsBlankPayload() {
        CreateEventRequest request = new CreateEventRequest("payment.succeeded", "   ");

        assertThatThrownBy(() -> eventService.createEvent(request, "idem-1"))
                .isInstanceOf(InvalidEventPayloadException.class);

        verifyNoInteractions(eventRepository, deliveryRepository, eventWriter);
    }

    @Test
    void replaysExistingEventForTheSameRequest() {
        Event event = new Event("payment.succeeded", "{\"amount\":100}", "idem-1");
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\": 100}"
        );

        when(eventRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(event));
        when(deliveryRepository.countByEvent_Id(any())).thenReturn(3L);

        CreateEventResult result = eventService.createEvent(request, "idem-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.response().deliveriesCreated()).isEqualTo(3);
        verifyNoInteractions(eventWriter);
    }

    @Test
    void rejectsKeyReusedWithDifferentPayload() {
        Event event = new Event("payment.succeeded", "{\"amount\":100}", "idem-1");
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\":200}"
        );

        when(eventRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.createEvent(request, "idem-1"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key already used for a different event");

        verifyNoInteractions(eventWriter);
    }

    @Test
    void rejectsKeyReusedWithDifferentType() {
        Event event = new Event("payment.succeeded", "{\"amount\":100}", "idem-1");
        CreateEventRequest request = new CreateEventRequest(
                "order.created",
                "{\"amount\":100}"
        );

        when(eventRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.createEvent(request, "idem-1"))
                .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(eventWriter);
    }

    @Test
    void replaysWhenConcurrentInsertWinsTheRace() {
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\":100}"
        );
        Event existing = new Event("payment.succeeded", "{\"amount\":100}", "idem-1");

        when(eventRepository.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(eventWriter.createEvent(request, "idem-1"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(deliveryRepository.countByEvent_Id(any())).thenReturn(2L);

        CreateEventResult result = eventService.createEvent(request, "idem-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.response().deliveriesCreated()).isEqualTo(2);
    }

    @Test
    void rethrowsWhenDuplicateKeyIsNotFromIdempotency() {
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\":100}"
        );
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "some other constraint"
        );

        when(eventRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(eventWriter.createEvent(request, "idem-1")).thenThrow(failure);

        assertThatThrownBy(() -> eventService.createEvent(request, "idem-1"))
                .isSameAs(failure);
    }
}
