package io.github.georgedanicico.lockin.cli.boundary;

import org.junit.jupiter.api.Test;

import static io.github.georgedanicico.lockin.TestArguments.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTests {

	@Test
	void readsAnOptionsValue() {
		assertThat(Command.option(args("--day=2026-11-20"), "day")).hasValue("2026-11-20");
		assertThat(Command.option(args("--force", "--day=2026-11-20"), "day")).hasValue("2026-11-20");
		assertThat(Command.option(args(), "day")).isEmpty();
	}

	@Test
	void anOptionWithoutAValueIsAnError() {
		assertThatThrownBy(() -> Command.option(args("--day"), "day")).hasMessage("--day needs a value: --day=…");
		assertThatThrownBy(() -> Command.option(args("--day="), "day")).hasMessage("--day needs a value: --day=…");
		assertThatThrownBy(() -> Command.option(args("--day", "2026-11-20"), "day"))
			.as("the value goes after =")
			.hasMessage("--day needs a value: --day=…");
	}

	@Test
	void anOptionGivenTwiceIsAnError() {
		assertThatThrownBy(() -> Command.option(args("--day=2026-11-20", "--day=2026-11-21"), "day"))
			.hasMessage("--day may be given only once");
	}

}
