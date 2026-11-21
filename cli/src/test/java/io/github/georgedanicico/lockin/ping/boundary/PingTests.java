package io.github.georgedanicico.lockin.ping.boundary;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestDatabase.class)
class PingTests {

	@Autowired
	Ping ping;

	@Autowired
	LockinProperties properties;

	@Test
	void answersOk() {
		assertThat(ping.run(List.of())).isEqualTo(Map.of("ok", true));
	}

	@Test
	void bindsTheSettings() {
		assertThat(properties.zone()).isEqualTo(ZoneId.of("Europe/Bucharest"));
		assertThat(properties.maxBackfillDays()).isEqualTo(7);
	}

}
