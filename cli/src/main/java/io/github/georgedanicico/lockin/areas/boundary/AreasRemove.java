package io.github.georgedanicico.lockin.areas.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.cli.boundary.Command;

import org.springframework.stereotype.Component;

/**
 * {@code lockin areas remove <slug>...}: sets one or more areas inactive. They are never deleted,
 * so their entries keep their area. Prints the removed slugs.
 */
@Component
class AreasRemove implements Command {

	private final AreaRepository areas;

	AreasRemove(AreaRepository areas) {
		this.areas = areas;
	}

	@Override
	public String name() {
		return "areas remove";
	}

	@Override
	public List<String> run(List<String> slugs) {
		areas.remove(slugs);
		return slugs;
	}

}
