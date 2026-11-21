package io.github.georgedanicico.lockin.cli.boundary;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsTests {

	@Test
	void readsAnOptionsValue() {
		assertThat(Options.value(List.of("--day", "2026-11-20"), "--day")).hasValue("2026-11-20");
		assertThat(Options.value(List.of(), "--day")).isEmpty();
	}

	@Test
	void anOptionWithoutAValueIsAnError() {
		assertThatThrownBy(() -> Options.value(List.of("--day"), "--day")).hasMessage("--day needs a value");
		assertThatThrownBy(() -> Options.value(List.of("--day", "--force"), "--day"))
			.hasMessage("--day needs a value");
	}

	@Test
	void readsAFlag() {
		assertThat(Options.flag(List.of("--force"), "--force")).isTrue();
		assertThat(Options.flag(List.of(), "--force")).isFalse();
	}

}
