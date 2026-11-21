package io.github.georgedanicico.lockin.review.boundary;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import io.github.georgedanicico.lockin.cli.boundary.Command;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.review.control.WeekRepository;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.AreaTotal;
import io.github.georgedanicico.lockin.review.entity.LoggedDay;
import io.github.georgedanicico.lockin.review.entity.LoggedEntry;
import io.github.georgedanicico.lockin.review.entity.Review;
import io.github.georgedanicico.lockin.review.entity.WeekReport;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * {@code lockin week [--week-start=YYYY-MM-DD]}: everything the weekly review needs. Default: the
 * last completed Monday–Sunday week.
 */
@Component
@RegisterReflectionForBinding({ WeekReport.class, AreaTotal.class, LoggedDay.class, LoggedEntry.class, Review.class,
		AreaScore.class })
class Week implements Command {

	private final WeekRepository weeks;

	private final LockinProperties properties;

	Week(WeekRepository weeks, LockinProperties properties) {
		this.weeks = weeks;
		this.properties = properties;
	}

	@Override
	public String name() {
		return "week";
	}

	@Override
	public WeekReport run(ApplicationArguments args) {
		var weekStart = Command.option(args, "week-start")
			.map(Week::monday)
			.orElseGet(() -> properties.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1));
		return weeks.report(weekStart);
	}

	private static LocalDate monday(String value) {
		LocalDate day;
		try {
			day = LocalDate.parse(value);
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("--week-start must be a date, YYYY-MM-DD: " + value);
		}
		if (day.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("--week-start must be a Monday: " + value);
		}
		return day;
	}

}
