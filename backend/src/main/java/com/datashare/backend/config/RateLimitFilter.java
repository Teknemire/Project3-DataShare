package com.datashare.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limiteur en mémoire pour une instance ; l'adresse socket reste la seule adresse de confiance. */
@Component
@Order(-120)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

	private static final int MAX_CLIENTS = 10_000;
	private final Map<String, Window> clients = new HashMap<>();
	private final boolean enabled;
	private final int requestLimit;
	private final int authLimit;
	private final int uploadLimit;
	private final int downloadLimit;
	private final Clock clock;
	private long lastCleanup;

	@Autowired
	public RateLimitFilter(
			@Value("${app.rate-limit.enabled:true}") boolean enabled,
			@Value("${app.rate-limit.requests-per-minute:120}") int requestLimit,
			@Value("${app.rate-limit.auth-per-minute:10}") int authLimit,
			@Value("${app.rate-limit.uploads-per-minute:10}") int uploadLimit,
			@Value("${app.rate-limit.downloads-per-minute:20}") int downloadLimit) {
		this(enabled, requestLimit, authLimit, uploadLimit, downloadLimit, Clock.systemUTC());
	}

	RateLimitFilter(boolean enabled, int requestLimit, int authLimit, int uploadLimit,
			int downloadLimit, Clock clock) {
		if (requestLimit < 1 || authLimit < 1 || uploadLimit < 1 || downloadLimit < 1) {
			throw new IllegalArgumentException("Les limites de débit doivent être positives.");
		}
		this.enabled = enabled;
		this.requestLimit = requestLimit;
		this.authLimit = authLimit;
		this.uploadLimit = uploadLimit;
		this.downloadLimit = downloadLimit;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !enabled || !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String action = action(request);
		long retryAfter = claim(request.getRemoteAddr(), action);
		if (retryAfter > 0) {
			log.warn("event=security action=rate_limit outcome=denied endpoint_group={} status=429", action);
			response.setStatus(429);
			response.setHeader("Retry-After", Long.toString(retryAfter));
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Trop de requêtes. Réessayez dans une minute.\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	private synchronized long claim(String address, String action) {
		long now = clock.millis();
		if (now - lastCleanup >= 60_000) {
			clients.values().removeIf(window -> now - window.startedAt >= 60_000);
			lastCleanup = now;
		}
		Window window = clients.get(address);
		if (window == null) {
			if (clients.size() >= MAX_CLIENTS) return 60;
			window = new Window(now);
			clients.put(address, window);
		} else if (now - window.startedAt >= 60_000) {
			window = new Window(now);
			clients.put(address, window);
		}
		int actionLimit = switch (action) {
			case "auth" -> authLimit;
			case "upload" -> uploadLimit;
			case "download" -> downloadLimit;
			default -> requestLimit;
		};
		int used = window.actions.getOrDefault(action, 0);
		if (window.requests >= requestLimit || used >= actionLimit) {
			return Math.max(1, (60_000 - (now - window.startedAt) + 999) / 1_000);
		}
		window.requests++;
		window.actions.put(action, used + 1);
		return 0;
	}

	private String action(HttpServletRequest request) {
		String path = request.getRequestURI();
		if ("POST".equals(request.getMethod())) {
			if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) return "auth";
			if (path.equals("/api/files")) return "upload";
		}
		if (path.startsWith("/api/shares/") && path.endsWith("/download")) return "download";
		return "api";
	}

	private static final class Window {
		private final long startedAt;
		private final Map<String, Integer> actions = new HashMap<>();
		private int requests;

		private Window(long startedAt) {
			this.startedAt = startedAt;
		}
	}
}
