package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datashare.backend.exception.InvalidDownloadTicketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DownloadTicketServiceTest {

	private DownloadTicketService service;

	@BeforeEach
	void setUp() {
		byte[] secret = "test-secret-with-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8);
		service = new DownloadTicketService(new SecretKeySpec(secret, "HmacSHA256"));
		ReflectionTestUtils.setField(service, "ticketExpiration", Duration.ofMinutes(1));
	}

	@Test
	void generatesAndValidatesATicketBoundToTheShareToken() {
		DownloadTicketService.GeneratedTicket ticket = service.generate(
				"share-token",
				Instant.now().plusSeconds(3_600)
		);

		service.validate("share-token", ticket.value());

		assertThat(ticket.expiresAt()).isAfter(Instant.now());
	}

	@Test
	void rejectsATicketUsedWithAnotherShareToken() {
		String ticket = service.generate("first-token", Instant.now().plusSeconds(3_600)).value();

		assertThatThrownBy(() -> service.validate("second-token", ticket))
				.isInstanceOf(InvalidDownloadTicketException.class);
	}

	@Test
	void rejectsAnAlteredOrExpiredTicket() {
		String ticket = service.generate("share-token", Instant.now().plusSeconds(3_600)).value();
		String alteredTicket = ticket.substring(0, ticket.length() - 1) + "A";

		assertThatThrownBy(() -> service.validate("share-token", alteredTicket))
				.isInstanceOf(InvalidDownloadTicketException.class);

		ReflectionTestUtils.setField(service, "ticketExpiration", Duration.ZERO);
		String expiredTicket = service.generate("share-token", Instant.now().plusSeconds(3_600)).value();
		assertThatThrownBy(() -> service.validate("share-token", expiredTicket))
				.isInstanceOf(InvalidDownloadTicketException.class);
	}
}
