package com.datashare.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

	@Test
	void limitsAuthenticationAndResetsAfterAMinute() throws Exception {
		Clock clock = mock(Clock.class);
		when(clock.millis()).thenReturn(100_000L);
		RateLimitFilter filter = new RateLimitFilter(true, 100, 2, 10, 20, clock);
		assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
		assertThat(call(filter, "POST", "/api/auth/register", "127.0.0.1").getStatus()).isEqualTo(200);
		MockHttpServletResponse rejected = call(filter, "POST", "/api/auth/login", "127.0.0.1");
		assertThat(rejected.getStatus()).isEqualTo(429);
		assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
		assertThat(rejected.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
		assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.2").getStatus()).isEqualTo(200);
		when(clock.millis()).thenReturn(160_000L);
		assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
	}

	@Test
	void limitsUploadsBeforeTheRequestBodyIsReadAndIgnoresForwardedAddresses() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 100, 2, 1, 20);
		assertThat(call(filter, "POST", "/api/files", "127.0.0.1").getStatus()).isEqualTo(200);
		assertThat(call(filter, "POST", "/api/files", "127.0.0.1").getStatus()).isEqualTo(429);
		assertThat(call(filter, "GET", "/api/files", "127.0.0.1").getStatus()).isEqualTo(200);
	}

	@Test
	void limitsDownloadAuthorizationAcrossDifferentShareTokens() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 100, 2, 10, 1);
		assertThat(call(filter, "POST", "/api/shares/one/download", "127.0.0.1").getStatus()).isEqualTo(200);
		assertThat(call(filter, "POST", "/api/shares/two/download", "127.0.0.1").getStatus()).isEqualTo(429);
	}

	@Test
	void appliesAGlobalBudgetButKeepsHealthAvailable() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(true, 1, 2, 10, 20);
		assertThat(call(filter, "GET", "/api/files", "127.0.0.1").getStatus()).isEqualTo(200);
		assertThat(call(filter, "GET", "/api/auth/me", "127.0.0.1").getStatus()).isEqualTo(429);
		assertThat(call(filter, "GET", "/actuator/health", "127.0.0.1").getStatus()).isEqualTo(200);
	}

	private MockHttpServletResponse call(RateLimitFilter filter, String method, String path, String address)
			throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(address);
		request.addHeader("X-Forwarded-For", "spoofed-" + Math.random());
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
		filter.doFilter(request, response, chain);
		return response;
	}
}
