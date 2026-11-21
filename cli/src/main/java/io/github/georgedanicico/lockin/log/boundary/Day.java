package io.github.georgedanicico.lockin.log.boundary;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.log.control.LogRepository;
import io.github.georgedanicico.lockin.log.entity.Entry;
import io.github.georgedanicico.lockin.log.entity.Note;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** {@code lockin day [--day=YYYY-MM-DD]}: the notes of one day (default today), with their entries. */
@Component
@RegisterReflectionForBinding({ Note.class, Entry.class })
class Day implements Command {

	private final LogRepository log;

	private final LockinProperties properties;

	Day(LogRepository log, LockinProperties properties) {
		this.log = log;
		this.properties = properties;
	}

	@Override
	public String name() {
		return "day";
	}

	@Override
	public List<Note> run(ApplicationArguments args) {
		return log.day(Command.option(args, "day").map(Day::parse).orElseGet(properties::today));
	}

	private static LocalDate parse(String day) {
		try {
			return LocalDate.parse(day);
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("--day must be YYYY-MM-DD: " + day);
		}
	}

}
