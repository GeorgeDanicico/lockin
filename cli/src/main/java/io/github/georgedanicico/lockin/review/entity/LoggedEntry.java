package io.github.georgedanicico.lockin.review.entity;

import java.math.BigDecimal;

/** One logged entry. {@code area} is null for "other"; {@code quantity} is null when none was stated. */
public record LoggedEntry(String area, String description, BigDecimal quantity) {
}
