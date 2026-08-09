package com.fitness.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the bearer scheme so the Authorize button of Swagger UI works. The
 * rendered page is a direct input for the technical manual.
 */
@Configuration
public class OpenApiConfig
{
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI fitnessAppOpenApi()
    {
        var scheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Fitness App API")
                        .version("v1")
                        .description("Sistema de Gestión de Gimnasio · Práctica 1, Análisis y Diseño de Sistemas 1."))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, scheme))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
