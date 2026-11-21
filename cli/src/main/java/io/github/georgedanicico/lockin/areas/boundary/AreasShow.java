package io.github.georgedanicico.lockin.areas.boundary;

import java.util.List;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.cli.boundary.Command;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** {@code lockin areas show}: the active areas. */
@Component
@RegisterReflectionForBinding(Area.class)
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
	public List<Area> run(ApplicationArguments args) {
		return areas.active();
	}

}
