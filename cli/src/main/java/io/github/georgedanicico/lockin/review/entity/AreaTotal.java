package io.github.georgedanicico.lockin.review.entity;

import java.math.BigDecimal;

import io.github.georgedanicico.lockin.areas.entity.Unit;

/** An active area with what was actually done in a week, counted in the area's unit. */
public record AreaTotal(String slug, String name, Unit unit, BigDecimal weeklyTarget, BigDecimal weight, String rubric,
		BigDecimal actual) {
}
