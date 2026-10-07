package com.datashare.backend.dto;

import java.time.Instant;

public record DownloadAccessResponse(String downloadUrl, Instant expiresAt) {

	@Override
	public String toString() {
		return "DownloadAccessResponse[downloadUrl=***, expiresAt=" + expiresAt + "]";
	}
}
