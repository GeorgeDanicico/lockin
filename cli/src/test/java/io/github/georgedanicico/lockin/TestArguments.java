package io.github.georgedanicico.lockin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

/** A command's arguments, as the command line passes them after the command's name. */
public final class TestArguments {

	private TestArguments() {
	}

	public static ApplicationArguments args(String... args) {
		return new DefaultApplicationArguments(args);
	}

}
