package io.github.georgedanicico.lockin.cli.boundary;

import java.util.List;
import java.util.Optional;

/** Reads {@code --name value} options and {@code --flag} flags from a command's arguments. */
public final class Options {

	private Options() {
	}

	/** The value after {@code name}, or empty when the option isn't given. */
	public static Optional<String> value(List<String> args, String name) {
		int index = args.indexOf(name);
		if (index < 0) {
			return Optional.empty();
		}
		if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
			throw new IllegalArgumentException(name + " needs a value");
		}
		return Optional.of(args.get(index + 1));
	}

	public static boolean flag(List<String> args, String name) {
		return args.contains(name);
	}

}
