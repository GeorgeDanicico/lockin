package io.github.georgedanicico.lockin.log.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A saved entry.
 *
 * @param area the area's slug; null for none ("other")
 */
public record Entry(UUID id, String area, String description, BigDecimal quantity) {
}
