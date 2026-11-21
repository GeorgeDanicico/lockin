package io.github.georgedanicico.lockin.cli.boundary;

import java.util.List;

/** One CLI command. Its result is printed to stdout as JSON. */
public interface Command {

	String name();

	Object run(List<String> args);

}
