package io.github.georgedanicico.lockin;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * A Postgres 17 container with the lockin schema from {@code db/001_schema.sql}.
 * <p>
 * The datasource properties come from a {@link DynamicPropertyRegistrar} rather than
 * {@code @ServiceConnection}: the connection details {@code @ServiceConnection} registers are lost in
 * the AOT-processed context of the native tests, while a plain bean is kept.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

	@Bean
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17").withCopyFileToContainer(
				MountableFile.forHostPath("../db/001_schema.sql"), "/docker-entrypoint-initdb.d/001_schema.sql");
	}

	@Bean
	DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
		return registry -> {
			postgres.start(); // does nothing when it is already running
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.datasource.username", postgres::getUsername);
			registry.add("spring.datasource.password", postgres::getPassword);
		};
	}

}
