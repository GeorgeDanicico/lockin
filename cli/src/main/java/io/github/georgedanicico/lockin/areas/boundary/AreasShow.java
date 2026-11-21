package io.github.georgedanicico.lockin.areas.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.InactiveArea;
import io.github.georgedanicico.lockin.cli.boundary.Command;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * {@code lockin areas show [--inactive]}: the active areas, or with {@code --inactive} the inactive
 * ones, each with the day it was deactivated.
 */
@Component
@RegisterReflectionForBinding({ Area.class, InactiveArea.class })
class AreasShow implements Command {

	private final AreaRepository areas;

	AreasShow(AreaRepository areas) {
		this.areas = areas;
	}

	@Override
	public String name() {
		return "areas show";
	}

	@Override
	public List<?> run(ApplicationArguments args) {
		return args.containsOption("inactive") ? areas.inactive() : areas.active();
	}

}
