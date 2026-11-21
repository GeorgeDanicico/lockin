package io.github.georgedanicico.lockin.cli.boundary;

import io.github.georgedanicico.lockin.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestDatabase.class)
@ExtendWith(OutputCaptureExtension.class)
class CommandLineTests {

	@Autowired
	CommandLine commandLine;

	@Test
	void printsTheResultAsJsonOnStdout(CapturedOutput output) {
		run("ping");
		assertThat(output.getOut()).isEqualTo("{\"ok\":true}\n");
		assertThat(commandLine.getExitCode()).isZero();
	}

	@Test
	void findsTwoWordCommands(CapturedOutput output) {
		run("areas", "show");
		assertThat(output.getOut()).startsWith("[");
		assertThat(commandLine.getExitCode()).isZero();
	}

	@Test
	void noCommandExitsWith2(CapturedOutput output) {
		run();
		assertThat(output.getOut()).isEmpty();
		assertThat(output.getErr()).contains("{\"error\":\"no_command\",\"details\":{\"commands\":[");
		assertThat(commandLine.getExitCode()).isEqualTo(2);
	}

	@Test
	void unknownCommandExitsWith2(CapturedOutput output) {
		run("nope");
		assertThat(output.getOut()).isEmpty();
		assertThat(output.getErr()).contains("{\"error\":\"unknown_command\"").contains("areas save");
		assertThat(commandLine.getExitCode()).isEqualTo(2);
	}

	@Test
	void invalidInputExitsWith2(CapturedOutput output) {
		run("areas", "remove");
		assertThat(output.getOut()).isEmpty();
		assertThat(output.getErr()).contains("\"error\":\"invalid_input\"").contains("at least one slug is required");
		assertThat(commandLine.getExitCode()).isEqualTo(2);
	}

	private void run(String... args) {
		commandLine.run(new DefaultApplicationArguments(args));
	}

}
