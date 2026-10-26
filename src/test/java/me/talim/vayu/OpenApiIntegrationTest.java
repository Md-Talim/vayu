package me.talim.vayu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import me.talim.vayu.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class OpenApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Vayu API"))
                .andExpect(jsonPath("$.paths['/events']").exists())
                .andExpect(jsonPath("$.paths['/endpoints']").exists())
                .andExpect(jsonPath("$.paths['/deliveries']").exists())
                .andExpect(jsonPath("$.paths['/events'].post.parameters[0].name").value("Idempotency-Key"))
                .andExpect(jsonPath("$.components.schemas.ApiError").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc
                .perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
