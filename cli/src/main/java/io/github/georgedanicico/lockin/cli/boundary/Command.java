package io.github.georgedanicico.lockin.cli.boundary;

import java.util.Optional;

import org.springframework.boot.ApplicationArguments;

/**
 * One CLI command. It gets the arguments after its name, with options written {@code --name=value}
 * and flags {@code --name}. Its result is printed to stdout as JSON.
 */
public interface Command {

	String name();

	Object run(ApplicationArguments args);

	/** The value of {@code --name=value}, or empty when the option isn't given. */
	static Optional<String> option(ApplicationArguments args, String name) {
		var values = args.getOptionValues(name);
		if (values == null) {
			return Optional.empty();
		}
		if (values.isEmpty() || values.getFirst().isEmpty()) {
			throw new IllegalArgumentException("--" + name + " needs a value: --" + name + "=…");
		}
		if (values.size() > 1) {
			throw new IllegalArgumentException("--" + name + " may be given only once");
		}
		return Optional.of(values.getFirst());
	}

}
