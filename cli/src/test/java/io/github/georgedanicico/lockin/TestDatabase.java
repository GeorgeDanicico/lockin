package io.github.georgedanicico.lockin;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/** A Postgres 17 container with the lockin schema from {@code db/001_schema.sql}. */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17").withCopyFileToContainer(
				MountableFile.forHostPath("../db/001_schema.sql"), "/docker-entrypoint-initdb.d/001_schema.sql");
	}

}
