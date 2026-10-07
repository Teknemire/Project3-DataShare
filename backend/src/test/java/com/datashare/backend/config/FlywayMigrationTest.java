package com.datashare.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {
	@Test
	void adoptsAnExistingV1SchemaWithoutLosingItsRows() throws Exception {
		String url = "jdbc:h2:mem:baseline-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
		Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
		try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
				var statement = connection.createStatement()) {
			statement.execute("insert into users values ('00000000-0000-0000-0000-000000000001', 'existing@test', 'hash', current_timestamp)");
			statement.execute("drop table \"flyway_schema_history\"");
		}
		Flyway.configure().dataSource(url, "sa", "").baselineOnMigrate(true).baselineVersion("1")
				.load().migrate();
		try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery("select count(*) from users where email = 'existing@test'")) {
			assertThat(rows.next()).isTrue();
			assertThat(rows.getInt(1)).isEqualTo(1);
		}
	}
}
