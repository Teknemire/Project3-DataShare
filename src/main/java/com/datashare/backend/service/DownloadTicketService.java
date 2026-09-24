package com.datashare.backend.service;

import com.datashare.backend.exception.InvalidDownloadTicketException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DownloadTicketService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final SecretKey secretKey;

	@Value("${app.files.download-ticket-expiration}")
	private Duration ticketExpiration;

	public GeneratedTicket generate(String downloadToken, Instant shareExpiresAt) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(ticketExpiration);
		if (shareExpiresAt.isBefore(expiresAt)) {
			expiresAt = shareExpiresAt;
		}
		long expiresAtEpochSecond = expiresAt.getEpochSecond();
		String signature = BASE64_URL_ENCODER.encodeToString(sign(downloadToken, expiresAtEpochSecond));
		return new GeneratedTicket(expiresAtEpochSecond + "." + signature, expiresAt);
	}

	public void validate(String downloadToken, String ticket) {
		try {
			int separatorIndex = ticket == null ? -1 : ticket.indexOf('.');
			if (separatorIndex <= 0 || separatorIndex == ticket.length() - 1) {
				throw new InvalidDownloadTicketException();
			}

			long expiresAtEpochSecond = Long.parseLong(ticket.substring(0, separatorIndex));
			byte[] providedSignature = BASE64_URL_DECODER.decode(ticket.substring(separatorIndex + 1));
			byte[] expectedSignature = sign(downloadToken, expiresAtEpochSecond);

			if (Instant.now().getEpochSecond() >= expiresAtEpochSecond
					|| !MessageDigest.isEqual(providedSignature, expectedSignature)) {
				throw new InvalidDownloadTicketException();
			}
		} catch (IllegalArgumentException exception) {
			throw new InvalidDownloadTicketException();
		}
	}

	private byte[] sign(String downloadToken, long expiresAtEpochSecond) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(secretKey);
			String value = "datashare-download\n" + downloadToken + "\n" + expiresAtEpochSecond;
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Impossible de signer l'autorisation de téléchargement.", exception);
		}
	}

	public record GeneratedTicket(String value, Instant expiresAt) {
	}
}
