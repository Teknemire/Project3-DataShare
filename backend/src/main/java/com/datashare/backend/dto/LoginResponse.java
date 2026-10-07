package com.datashare.backend.dto;

import java.time.Instant;

public record LoginResponse(
		String accessToken,
		String tokenType,
		Instant expiresAt,
		UserResponse user
) {
	@Override
	public String toString() {
		return "LoginResponse[accessToken=***, tokenType=" + tokenType + ", expiresAt=" + expiresAt + "]";
	}
}
