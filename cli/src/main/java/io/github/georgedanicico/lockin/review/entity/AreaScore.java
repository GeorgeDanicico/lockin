package io.github.georgedanicico.lockin.review.entity;

import java.math.BigDecimal;

/** How one area did in a reviewed week, with its target as it was that week. */
public record AreaScore(String slug, BigDecimal actual, BigDecimal target, BigDecimal score, String comment) {

	public AreaScore {
		require(slug != null, "area score: slug is required");
		require(actual != null && actual.signum() >= 0, "area score " + slug + ": actual must be 0 or more");
		require(target != null && target.signum() > 0, "area score " + slug + ": target must be greater than 0");
		require(score != null && score.signum() >= 0 && score.compareTo(BigDecimal.TEN) <= 0,
				"area score " + slug + ": score must be from 0 to 10");
		require(comment != null && !comment.isBlank(), "area score " + slug + ": comment is required");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
