package io.github.georgedanicico.lockin.areas.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An area set inactive: not scored and taking no entries until it is made active again.
 *
 * @param deactivatedOn my local day it was set inactive; null if that was before the date was kept
 */
public record InactiveArea(String slug, String name, Unit unit, BigDecimal weeklyTarget, BigDecimal weight,
		String rubric, LocalDate deactivatedOn) {
}
