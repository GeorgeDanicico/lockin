package io.github.georgedanicico.lockin.ping.boundary;

import java.util.Map;

import io.github.georgedanicico.lockin.cli.boundary.Command;

import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code lockin ping}: checks that the database answers. */
@Component
class Ping implements Command {

	private final JdbcClient jdbc;

	Ping(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String name() {
		return "ping";
	}

	@Override
	public Object run(ApplicationArguments args) {
		jdbc.sql("select 1").query(Integer.class).single();
		return Map.of("ok", true);
	}

}
