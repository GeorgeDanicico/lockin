package io.github.georgedanicico.lockin.cli.boundary;

import java.io.Console;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads a command's JSON input from stdin: {@code echo '[…]' | lockin areas save}. */
@Component
public class Input {

	private final JsonMapper json;

	Input(JsonMapper json) {
		this.json = json;
	}

	public <T> T read(TypeReference<T> type) {
		Console console = System.console();
		if (console != null && console.isTerminal()) {
			throw new IllegalArgumentException("pipe the JSON input into stdin");
		}
		return json.readValue(System.in, type);
	}

}
