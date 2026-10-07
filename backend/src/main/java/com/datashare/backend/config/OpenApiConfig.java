package com.datashare.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
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
	OpenApiCustomizer rateLimitResponses() {
		return openApi -> openApi.getPaths().forEach((path, item) -> {
			if (path.startsWith("/api/")) {
				item.readOperations().forEach(operation -> operation.getResponses().addApiResponse("429",
						new ApiResponse().description("Limite de débit atteinte : RATE_LIMIT_EXCEEDED")
								.addHeaderObject("Retry-After", new Header().description("Délai en secondes")
										.schema(new IntegerSchema()))
								.content(new Content().addMediaType("application/json", new MediaType()
										.schema(new Schema<>().$ref("#/components/schemas/ApiError"))))));
			}
		});
	}

	@Bean
	OpenApiCustomizer optionalUploadAuthentication() {
		return openApi -> openApi.getPaths().get("/api/files").getPost().setSecurity(List.of(
				new SecurityRequirement(),
				new SecurityRequirement().addList("bearerAuth")
		));
	}
}
