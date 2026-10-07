package com.datashare.backend.dto;

public record DownloadRequest(String password) {

	@Override
	public String toString() {
		return "DownloadRequest[password=***]";
	}
}
