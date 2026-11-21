package io.github.georgedanicico.lockin.log.boundary;

import java.util.List;
import java.util.UUID;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.boundary.Options;
import io.github.georgedanicico.lockin.log.control.LogRepository;
import io.github.georgedanicico.lockin.log.entity.Entry;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

/** {@code lockin entry delete --id <uuid>}: deletes one entry. Prints the deleted entry. */
@Component
@RegisterReflectionForBinding(Entry.class)
class EntryDelete implements Command {

	private final LogRepository log;

	EntryDelete(LogRepository log) {
		this.log = log;
	}

	@Override
	public String name() {
		return "entry delete";
	}

	@Override
	public Entry run(List<String> args) {
		String id = Options.value(args, "--id").orElseThrow(() -> new IllegalArgumentException("--id is required"));
		return log.deleteEntry(UUID.fromString(id));
	}

}
