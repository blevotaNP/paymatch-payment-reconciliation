package com.paymatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().replaceWithSchema(BigDecimal.class,
                new StringSchema().pattern("^-?\\d+(\\.\\d{1,2})?$").example("1230.50"));
    }

    @Bean
    OpenAPI paymatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PayMatch API")
                .description("Bank statement import and payment reconciliation. "
                        + "Log in via POST /api/auth/login first; amounts are decimal strings.")
                .version("0.1.0"));
    }
}
