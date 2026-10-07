package com.datashare.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
		title = "DataShare API",
		version = "1.0",
		description = "API REST de transfert de fichiers DataShare"
))
@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT"
)
public class OpenApiConfig {

	@Bean
	OpenApiCustomizer optionalUploadAuthentication() {
		return openApi -> openApi.getPaths().get("/api/files").getPost().setSecurity(List.of(
				new SecurityRequirement(),
				new SecurityRequirement().addList("bearerAuth")
		));
	}
}
