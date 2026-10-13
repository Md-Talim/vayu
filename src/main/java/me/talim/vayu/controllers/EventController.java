package me.talim.vayu.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.services.EventService;

@RestController
@RequestMapping("/events")
@Validated
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<CreateEventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @Valid @NotBlank @Size(max = 255)
            @RequestHeader(required = true, name = "Idempotency-Key") String idempotencyKey
    ) {
        CreateEventResult result = eventService.createEvent(request, idempotencyKey);

        if (result.replay()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .header("Idempotency-Replayed", "true")
                    .body(result.response());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }
}
