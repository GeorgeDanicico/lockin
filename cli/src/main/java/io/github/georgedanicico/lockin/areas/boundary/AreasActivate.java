package io.github.georgedanicico.lockin.areas.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.cli.boundary.Command;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * {@code lockin areas activate <slug>...}: makes one or more inactive areas active again, with their
 * target, weight and rubric unchanged. Prints the activated areas.
 */
@Component
@RegisterReflectionForBinding(Area.class)
class AreasActivate implements Command {

	private final AreaRepository areas;

	AreasActivate(AreaRepository areas) {
		this.areas = areas;
	}

	@Override
	public String name() {
		return "areas activate";
	}

	@Override
	public List<Area> run(ApplicationArguments args) {
		return areas.activate(args.getNonOptionArgs());
	}

}
