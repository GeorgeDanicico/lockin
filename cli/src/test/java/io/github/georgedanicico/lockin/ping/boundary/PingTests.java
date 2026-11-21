package io.github.georgedanicico.lockin.ping.boundary;

import java.time.ZoneId;
import java.util.Map;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static io.github.georgedanicico.lockin.TestArguments.args;
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
		assertThat(ping.run(args())).isEqualTo(Map.of("ok", true));
	}

	@Test
	void bindsTheSettings() {
		assertThat(properties.zone()).isEqualTo(ZoneId.of("Europe/Bucharest"));
		assertThat(properties.maxBackfillDays()).isEqualTo(7);
	}

}
