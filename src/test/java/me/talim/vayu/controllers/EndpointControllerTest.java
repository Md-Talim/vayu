package me.talim.vayu.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import me.talim.vayu.dto.CreateEndpointRequest;
import me.talim.vayu.dto.CreateEndpointResponse;
import me.talim.vayu.exception.ApiExceptionHandler;
import me.talim.vayu.services.EndpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EndpointController.class)
@Import(ApiExceptionHandler.class)
class EndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EndpointService endpointService;

    @Test
    void createsEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-14T12:00:00Z");

        CreateEndpointResponse response = new CreateEndpointResponse(
            id,
            "https://example.com/webhook",
            true,
            Set.of("payment.created"),
            createdAt,
            "generated-secret"
        );

        when(endpointService.createEndpoint(any(CreateEndpointRequest.class))).thenReturn(response);

        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "url": "https://example.com/webhook",
                              "eventTypes": ["payment.created"]
                            }
                        """
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/endpoints/" + id))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.url").value("https://example.com/webhook"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.eventTypes[0]").value("payment.created"))
            .andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00Z"))
            .andExpect(jsonPath("$.secret").value("generated-secret"));

        verify(endpointService).createEndpoint(any(CreateEndpointRequest.class));
    }

    @Test
    void rejectsEndpointWithoutUrl() throws Exception {
        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "eventTypes": ["payment.created"]
                            }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpointService);
    }

    @Test
    void rejectsEndpointWithInvalidUrl() throws Exception {
        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "url": "ftp://example.com/webhook",
                              "eventTypes": ["payment.created"]
                            }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpointService);
    }

    @Test
    void rejectsEndpointWithoutEventTypes() throws Exception {
        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "url": "https://example.com/webhook"
                            }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpointService);
    }

    @Test
    void rejectsEndpointWithEmptyEventTypes() throws Exception {
        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "url": "https://example.com/webhook",
                              "eventTypes": []
                            }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpointService);
    }

    @Test
    void rejectsEndpointWithBlankEventType() throws Exception {
        mockMvc
            .perform(
                post("/endpoints")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "url": "https://example.com/webhook",
                              "eventTypes": ["   "]
                            }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(endpointService);
    }
}
