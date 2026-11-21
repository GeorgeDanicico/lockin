package io.github.georgedanicico.lockin.log.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.boundary.Input;
import io.github.georgedanicico.lockin.cli.boundary.Options;
import io.github.georgedanicico.lockin.log.control.LogRepository;
import io.github.georgedanicico.lockin.log.entity.Entry;
import io.github.georgedanicico.lockin.log.entity.LogRequest;
import io.github.georgedanicico.lockin.log.entity.Note;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * {@code lockin log [--force]}: reads a note and its entries from stdin and saves them. Prints the
 * saved note. {@code --force} allows a day older than {@code max-backfill-days}.
 */
@Component
@RegisterReflectionForBinding({ LogRequest.class, LogRequest.Item.class, Note.class, Entry.class })
class Log implements Command {

	private final Input input;

	private final LogRepository log;

	Log(Input input, LogRepository log) {
		this.input = input;
		this.log = log;
	}

	@Override
	public String name() {
		return "log";
	}

	@Override
	public Note run(List<String> args) {
		LogRequest request = input.read(new TypeReference<LogRequest>() {
		});
		return log.save(request, Options.flag(args, "--force"));
	}

}
