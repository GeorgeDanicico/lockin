package io.github.georgedanicico.lockin.cli.boundary;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the command named by the first one or two arguments ({@code ping}, {@code areas show}), with
 * the arguments after its name ({@code entry delete --id=<uuid>}).
 * The result goes to stdout as JSON; an error goes to stderr as {@code {"error":…,"details":…}}
 * with exit code 2 (bad call or invalid input) or 1 (failure).
 */
@Component
@RegisterReflectionForBinding(CommandLine.Failure.class)
class CommandLine implements ApplicationRunner, ExitCodeGenerator {

	record Failure(String error, Object details) {
	}

	private final Map<String, Command> commands;

	private final JsonMapper json;

	private int exitCode;

	CommandLine(List<Command> commands, JsonMapper json) {
		this.commands = commands.stream().collect(Collectors.toMap(Command::name, Function.identity()));
		this.json = json;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		exitCode = 0;
		var args = List.of(arguments.getSourceArgs());
		if (args.isEmpty()) {
			fail(2, "no_command", Map.of("commands", commandNames()));
			return;
		}
		for (var words = Math.min(2, args.size()); words > 0; words--) {
			var command = commands.get(String.join(" ", args.subList(0, words)));
			if (command != null) {
				execute(command, args.subList(words, args.size()));
				return;
			}
		}
		fail(2, "unknown_command", Map.of("commands", commandNames()));
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	/** Runs the command with the arguments after its name, so its options and other arguments are its own. */
	private void execute(Command command, List<String> args) {
		try {
			var result = command.run(new DefaultApplicationArguments(args.toArray(String[]::new)));
			System.out.println(json.writeValueAsString(result));
		}
		catch (IllegalArgumentException | JacksonException e) {
			fail(2, "invalid_input", message(e));
		}
		catch (RuntimeException e) {
			fail(1, "command_failed", message(e));
		}
	}

	private void fail(int code, String error, Object details) {
		System.err.println(json.writeValueAsString(new Failure(error, details)));
		exitCode = code;
	}

	private List<String> commandNames() {
		return commands.keySet().stream().sorted().toList();
	}

	private static String message(Exception e) {
		return String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
	}

}
