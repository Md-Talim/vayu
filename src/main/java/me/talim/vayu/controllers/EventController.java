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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResponse;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.exception.ApiError;
import me.talim.vayu.services.EventService;

@RestController
@RequestMapping("/events")
@Validated
@Tag(name = "Events", description = "Create events and fan them out to subscribed endpoints")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(
            summary = "Create an event",
            description = "Stores an event and creates one delivery per active subscription. "
                    + "Reusing an idempotency key with the same event returns the original event."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Event created"),
            @ApiResponse(
                    responseCode = "200",
                    description = "Event replayed for an existing idempotency key"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request, payload, or idempotency key",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency key already used for a different event",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    public ResponseEntity<CreateEventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Unique key for this event. Reusing it with a different payload returns 409."
            )
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
