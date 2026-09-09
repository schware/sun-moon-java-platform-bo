package com.sunmoon.bo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI boOpenApi() {
        // The servers URL must match this service's context-path, and must
        // not carry the server's IP — same rule as the sibling services.
        return new OpenAPI()
                .info(new Info()
                        .title("sun-moon-java-platform-bo API")
                        .description("Back Office — operator accounts, 공통코드, and device master data.")
                        .version("0.1.0"))
                .servers(List.of(new Server().url("/bo")));
    }
}
