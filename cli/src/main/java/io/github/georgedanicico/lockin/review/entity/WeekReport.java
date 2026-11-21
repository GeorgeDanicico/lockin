package io.github.georgedanicico.lockin.review.entity;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the weekly review needs: the active areas with their actuals, the logged days, the days
 * with no note, and the last reviews before this week, newest first.
 */
public record WeekReport(LocalDate weekStart, LocalDate weekEnd, List<AreaTotal> areas, List<LoggedDay> days,
		List<LocalDate> unloggedDays, List<Review> recentReviews) {
}
