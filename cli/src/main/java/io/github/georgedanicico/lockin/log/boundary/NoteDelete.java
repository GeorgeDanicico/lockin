package io.github.georgedanicico.lockin.log.boundary;

import java.util.UUID;

import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.log.control.LogRepository;
import io.github.georgedanicico.lockin.log.entity.Entry;
import io.github.georgedanicico.lockin.log.entity.Note;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** {@code lockin note delete --id=<uuid>}: deletes a note and its entries. Prints the deleted note. */
@Component
@RegisterReflectionForBinding({ Note.class, Entry.class })
class NoteDelete implements Command {

	private final LogRepository log;

	NoteDelete(LogRepository log) {
		this.log = log;
	}

	@Override
	public String name() {
		return "note delete";
	}

	@Override
	public Note run(ApplicationArguments args) {
		var id = Command.option(args, "id").orElseThrow(() -> new IllegalArgumentException("--id is required"));
		return log.deleteNote(UUID.fromString(id));
	}

}
