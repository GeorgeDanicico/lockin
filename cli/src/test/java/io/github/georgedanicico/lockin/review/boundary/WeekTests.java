package io.github.georgedanicico.lockin.review.boundary;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.AreaTotal;
import io.github.georgedanicico.lockin.review.entity.LoggedDay;
import io.github.georgedanicico.lockin.review.entity.LoggedEntry;
import io.github.georgedanicico.lockin.review.entity.Review;
import io.github.georgedanicico.lockin.review.entity.WeekReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class WeekTests {

	@Autowired
	Week week;

	@Autowired
	AreaRepository areas;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	LockinProperties properties;

	LocalDate monday;

	@BeforeEach
	void saveAreas() {
		monday = properties.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
		areas.save(List.of(area("chess", Unit.SESSIONS), area("gym", Unit.SESSIONS), area("pushups", Unit.COUNT),
				area("reading", Unit.MINUTES), area("spanish", Unit.DAYS), area("writing", Unit.MINUTES)));
		areas.remove(List.of("chess"));
	}

	@Test
	void countsEachAreaInItsUnit() {
		UUID first = note(monday, "Read 10 minutes, 30 pushups, gym, Duolingo, a game of chess");
		entry(first, "reading", "Read a book", 10);
		entry(first, "pushups", "Pushups", 30);
		entry(first, "gym", "Gym", null);
		entry(first, "spanish", "Duolingo lesson", null);
		entry(first, "chess", "A game of chess", null);
		UUID second = note(monday, "Read 20 more minutes, a Spanish podcast, cooked dinner");
		entry(second, "reading", "Read a book", 20);
		entry(second, "spanish", "Spanish podcast", null);
		entry(second, null, "Cooked dinner", null);
		UUID wednesday = note(monday.plusDays(2), "Gym, 20 pushups, Duolingo");
		entry(wednesday, "gym", "Gym", null);
		entry(wednesday, "pushups", "Pushups", 20);
		entry(wednesday, "spanish", "Duolingo lesson", null);
		entry(note(monday.minusDays(1), "Read 100 minutes"), "reading", "Read a book", 100);
		entry(note(monday.plusDays(7), "Read 100 minutes"), "reading", "Read a book", 100);

		WeekReport report = week.run(List.of("--week-start", monday.toString()));

		assertThat(report.weekStart()).isEqualTo(monday);
		assertThat(report.weekEnd()).isEqualTo(monday.plusDays(6));
		assertThat(report.areas()).as("active areas only")
			.extracting(AreaTotal::slug, total -> total.actual().intValueExact())
			.containsExactly(tuple("gym", 2), tuple("pushups", 50), tuple("reading", 30),
					tuple("spanish", 2), tuple("writing", 0));
		assertThat(report.days()).extracting(LoggedDay::day).containsExactly(monday, monday.plusDays(2));
		LoggedDay day = report.days().get(0);
		assertThat(day.notes()).containsExactly("Read 10 minutes, 30 pushups, gym, Duolingo, a game of chess",
				"Read 20 more minutes, a Spanish podcast, cooked dinner");
		assertThat(day.entries()).extracting(LoggedEntry::area, LoggedEntry::description)
			.containsExactly(tuple("reading", "Read a book"), tuple("pushups", "Pushups"), tuple("gym", "Gym"),
					tuple("spanish", "Duolingo lesson"), tuple("chess", "A game of chess"), tuple("reading", "Read a book"),
					tuple("spanish", "Spanish podcast"), tuple(null, "Cooked dinner"));
		assertThat(day.entries().get(0).quantity()).isEqualByComparingTo("10");
		assertThat(day.entries().get(2).quantity()).isNull();
		assertThat(report.unloggedDays()).containsExactly(monday.plusDays(1), monday.plusDays(3), monday.plusDays(4),
				monday.plusDays(5), monday.plusDays(6));
	}

	@Test
	void defaultsToTheLastCompletedWeek() {
		WeekReport report = week.run(List.of());

		assertThat(report.weekStart().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(report.weekEnd()).isBefore(properties.today()).isAfterOrEqualTo(properties.today().minusDays(7));
		assertThat(report.days()).isEmpty();
		assertThat(report.unloggedDays()).hasSize(7);
		assertThat(report.areas()).allSatisfy(total -> assertThat(total.actual()).isZero());
	}

	@Test
	void includesTheLast4ReviewsBeforeTheWeek() {
		for (int weeks = -5; weeks <= 1; weeks++) {
			review(monday.plusWeeks(weeks), "9.5");
		}

		WeekReport report = week.run(List.of("--week-start", monday.toString()));

		assertThat(report.recentReviews()).extracting(Review::weekStart)
			.containsExactly(monday.minusWeeks(1), monday.minusWeeks(2), monday.minusWeeks(3), monday.minusWeeks(4));
		Review latest = report.recentReviews().get(0);
		assertThat(latest.grade()).isEqualTo("A");
		assertThat(latest.areaScores()).extracting(AreaScore::slug, AreaScore::comment)
			.containsExactly(tuple("gym", "Two sessions"));
	}

	@Test
	void rejectsAWeekStartThatIsNotAMonday() {
		assertThatThrownBy(() -> week.run(List.of("--week-start", monday.plusDays(1).toString())))
			.hasMessage("--week-start must be a Monday: " + monday.plusDays(1));
		assertThatThrownBy(() -> week.run(List.of("--week-start", "last-monday")))
			.hasMessage("--week-start must be a date, YYYY-MM-DD: last-monday");
		assertThatThrownBy(() -> week.run(List.of("--week-start"))).hasMessage("--week-start needs a value");
	}

	private static Area area(String slug, Unit unit) {
		return new Area(slug, slug, unit, BigDecimal.TEN, null, "Rubric");
	}

	private UUID note(LocalDate day, String text) {
		return jdbc.sql("""
				insert into lockin.daily_notes (day, raw_text, created_at)
				values (:day, :text, clock_timestamp())
				returning id
				""").param("day", day).param("text", text).query(UUID.class).single();
	}

	private void entry(UUID note, String area, String description, Integer quantity) {
		jdbc.sql("""
				insert into lockin.entries (note_id, area_id, description, quantity, created_at)
				values (:note, (select id from lockin.areas where slug = :area), :description, :quantity, clock_timestamp())
				""")
			.param("note", note)
			.param("area", area)
			.param("description", description)
			.param("quantity", quantity)
			.update();
	}

	private void review(LocalDate weekStart, String score) {
		jdbc.sql("""
				insert into lockin.reviews (week_start, score, area_scores, summary)
				values (:weekStart, :score,
				        '[{"slug": "gym", "actual": 2, "target": 10, "score": 5, "comment": "Two sessions"}]', 'A week')
				""").param("weekStart", weekStart).param("score", new BigDecimal(score)).update();
	}

}
