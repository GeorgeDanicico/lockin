package io.github.georgedanicico.lockin.areas.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.boundary.Input;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * {@code lockin areas save}: reads an array of one or more areas from stdin and creates or updates
 * each one by slug. Prints the saved areas.
 */
@Component
@RegisterReflectionForBinding(Area.class)
class AreasSave implements Command {

	private final Input input;

	private final AreaRepository areas;

	AreasSave(Input input, AreaRepository areas) {
		this.input = input;
		this.areas = areas;
	}

	@Override
	public String name() {
		return "areas save";
	}

	@Override
	public List<Area> run(List<String> args) {
		List<Area> saved = input.read(new TypeReference<List<Area>>() {
		});
		areas.save(saved);
		return saved;
	}

}
