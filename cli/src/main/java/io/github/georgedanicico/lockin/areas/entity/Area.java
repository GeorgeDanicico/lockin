package io.github.georgedanicico.lockin.areas.entity;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** A goal: something I track, with its weekly target. Identified by its slug. */
public record Area(String slug, String name, Unit unit, BigDecimal weeklyTarget, BigDecimal weight, String rubric) {

	private static final Pattern SLUG = Pattern.compile("[a-z][a-z0-9-]*");

	public Area {
		require(slug != null && SLUG.matcher(slug).matches() && !slug.equals("other"),
				"slug must be lowercase letters, digits and dashes, start with a letter, and not be 'other': " + slug);
		require(name != null && !name.isBlank(), "area " + slug + ": name is required");
		require(unit != null, "area " + slug + ": unit is required");
		require(weeklyTarget != null && weeklyTarget.signum() > 0, "area " + slug + ": weekly_target must be greater than 0");
		weight = (weight != null) ? weight : BigDecimal.ONE;
		require(weight.signum() > 0, "area " + slug + ": weight must be greater than 0");
		require(rubric != null && !rubric.isBlank(), "area " + slug + ": rubric is required");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
