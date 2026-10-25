package me.talim.vayu.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.exception.ApiExceptionHandler;
import me.talim.vayu.exception.IdempotencyConflictException;
import me.talim.vayu.exception.InvalidEventPayloadException;
import me.talim.vayu.services.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventControllerTest {

    private static final String VALID_BODY = """
            {"type":"payment.succeeded","payload":"{\\"amount\\":100}"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void createsEvent() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-25T10:00:00Z");

        CreateEventResponse response = new CreateEventResponse(
                id,
                "payment.succeeded",
                "idem-1",
                createdAt,
                2
        );
        when(eventService.createEvent(any(CreateEventRequest.class), eq("idem-1")))
                .thenReturn(new CreateEventResult(response, false));

        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("payment.succeeded"))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-1"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-25T10:00:00Z"))
                .andExpect(jsonPath("$.deliveriesCreated").value(2))
                .andExpect(header().doesNotExist("Idempotency-Replayed"));

        verify(eventService).createEvent(any(CreateEventRequest.class), eq("idem-1"));
    }

    @Test
    void replaysExistingEvent() throws Exception {
        UUID id = UUID.randomUUID();
        CreateEventResponse response = new CreateEventResponse(
                id,
                "payment.succeeded",
                "idem-1",
                Instant.parse("2026-10-25T10:00:00Z"),
                2
        );
        when(eventService.createEvent(any(CreateEventRequest.class), eq("idem-1")))
                .thenReturn(new CreateEventResult(response, true));

        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void rejectsKeyReusedWithDifferentPayload() throws Exception {
        when(eventService.createEvent(any(CreateEventRequest.class), eq("idem-1")))
                .thenThrow(new IdempotencyConflictException(
                        "Idempotency key already used for a different event"
                ));

        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Idempotency key already used for a different event"
                ));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        when(eventService.createEvent(any(CreateEventRequest.class), eq("idem-1")))
                .thenThrow(new InvalidEventPayloadException("Event payload must be valid JSON"));

        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"type":"payment.succeeded","payload":"not-json"}
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc
                .perform(
                        post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value(
                        "required header 'Idempotency-Key' is missing"
                ));

        verifyNoInteractions(eventService);
    }

    @Test
    void rejectsEventWithoutType() throws Exception {
        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"payload":"{\\"amount\\":100}"}
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));

        verifyNoInteractions(eventService);
    }

    @Test
    void rejectsEmptyPayload() throws Exception {
        mockMvc
                .perform(
                        post("/events")
                                .header("Idempotency-Key", "idem-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"type":"payment.succeeded","payload":""}
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("payload"));

        verifyNoInteractions(eventService);
    }
}
