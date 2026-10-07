package com.datashare.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
@Slf4j
public class ApiRequestMetricsFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		long startedAt = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			int status = response.getStatus();
			String group = endpointGroup(request.getRequestURI());
			if (status == 401 || status == 403
					|| (group.equals("auth") && request.getMethod().equals("POST"))
					|| request.getMethod().equals("DELETE")) {
				log.info("event=security action={} endpoint_group={} outcome={} status={}",
						request.getMethod().equals("DELETE") ? "deletion" : "authentication",
						group, status < 400 ? "allowed" : "denied", status);
			}
			long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
			log.info(
					"event=http_request method={} endpoint_group={} status={} duration_ms={} request_bytes={}",
					request.getMethod(),
					endpointGroup(request.getRequestURI()),
					response.getStatus(),
					durationMs,
					Math.max(0, request.getContentLengthLong())
			);
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	private String endpointGroup(String requestUri) {
		if (requestUri.startsWith("/api/auth")) {
			return "auth";
		}
		if (requestUri.startsWith("/api/files")) {
			return "files";
		}
		if (requestUri.startsWith("/api/shares")) {
			return "shares";
		}
		if (requestUri.startsWith("/api/users")) {
			return "users";
		}
		return "other";
	}
}
