package io.github.georgedanicico.lockin.review.entity;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * The grading of one Monday–Sunday week. The grade is worked out from the score (A ≥ 9, B ≥ 7.5,
 * C ≥ 6, D ≥ 4, F below); a given one is ignored.
 */
public record Review(LocalDate weekStart, BigDecimal score, String grade, List<AreaScore> areaScores,
		String summary) {

	public Review {
		require(weekStart != null && weekStart.getDayOfWeek() == DayOfWeek.MONDAY,
				"week_start must be a Monday: " + weekStart);
		require(score != null && score.signum() >= 0 && score.compareTo(BigDecimal.TEN) <= 0
				&& score.stripTrailingZeros().scale() <= 1, "score must be from 0 to 10 with at most one decimal: " + score);
		grade = grade(score);
		require(areaScores != null, "area_scores is required");
		require(summary != null && !summary.isBlank() && summary.codePointCount(0, summary.length()) <= 1500,
				"summary must be 1 to 1500 characters");
	}

	private static String grade(BigDecimal score) {
		if (score.compareTo(new BigDecimal("9")) >= 0) {
			return "A";
		}
		if (score.compareTo(new BigDecimal("7.5")) >= 0) {
			return "B";
		}
		if (score.compareTo(new BigDecimal("6")) >= 0) {
			return "C";
		}
		if (score.compareTo(new BigDecimal("4")) >= 0) {
			return "D";
		}
		return "F";
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
