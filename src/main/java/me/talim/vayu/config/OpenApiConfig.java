package me.talim.vayu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI vayuOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vayu API")
                        .version("v1")
                        .description("""
                                Webhook delivery service API.

                                Create endpoints and events, then inspect deliveries and attempts.
                                """)
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/md-talim/vayu/blob/main/LICENSE")));
    }
}
