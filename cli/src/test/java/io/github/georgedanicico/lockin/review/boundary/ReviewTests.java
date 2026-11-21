package io.github.georgedanicico.lockin.review.boundary;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.Review;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

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
class ReviewTests {

	private static final String READING = """
			{ "slug": "reading", "actual": 90, "target": 150, "score": 6, "comment": "Three short sessions" }""";

	private static final String SPANISH = """
			{ "slug": "spanish", "actual": 7, "target": 7, "score": 9, "comment": "A lesson every day" }""";

	private static final InputStream STDIN = System.in;

	@Autowired
	ReviewSave save;

	@Autowired
	Reviews reviews;

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
		areas.save(List.of(new Area("reading", "Reading", Unit.MINUTES, new BigDecimal("150"), null, "150 minutes"),
				new Area("spanish", "Spanish", Unit.DAYS, new BigDecimal("7"), new BigDecimal("2"), "A lesson every day")));
	}

	@AfterEach
	void restoreStdin() {
		System.setIn(STDIN);
	}

	@Test
	void saveSavesTheReviewAndReviewsShowsIt() {
		Review saved = save(review(monday, "8.0", READING, SPANISH).replaceFirst("\\{", "{ \"grade\": \"A\","));

		assertThat(saved.weekStart()).isEqualTo(monday);
		assertThat(saved.score()).isEqualByComparingTo("8");
		assertThat(saved.grade()).as("worked out from the score, not taken from the input").isEqualTo("B");
		assertThat(saved.areaScores()).extracting(AreaScore::slug, AreaScore::comment)
			.containsExactly(tuple("reading", "Three short sessions"), tuple("spanish", "A lesson every day"));
		assertThat(saved.areaScores().get(0).actual()).isEqualByComparingTo("90");
		assertThat(saved.areaScores().get(0).target()).isEqualByComparingTo("150");
		assertThat(saved.areaScores().get(1).score()).isEqualByComparingTo("9");
		assertThat(reviews.run(List.of())).containsExactly(saved);
	}

	@Test
	void saveReplacesTheWeeksReview() {
		save(review(monday, "8.0", READING, SPANISH));
		jdbc.sql("update lockin.reviews set created_at = created_at - interval '1 day', updated_at = updated_at - interval '1 day'")
			.update();

		Review replaced = save(review(monday, "7.9", READING, SPANISH).replace("a B.", "still a B."));

		assertThat(reviews.run(List.of("--last", "52"))).containsExactly(replaced);
		assertThat(replaced.score()).isEqualByComparingTo("7.9");
		assertThat(replaced.summary()).endsWith("still a B.");
		assertThat(jdbc.sql("select updated_at > created_at from lockin.reviews").query(Boolean.class).single()).isTrue();
	}

	@Test
	void saveAcceptsTheEdgesOfEachRule() {
		save(review(monday, "6.7", READING.replace("\"score\": 6", "\"score\": 0"),
				SPANISH.replace("\"score\": 9", "\"score\": 10"))
			.replaceAll("\"summary\": \".*\"", "\"summary\": \"" + "x".repeat(1500) + "\""));
		save(review(monday.minusWeeks(1), "8.1", READING, SPANISH));

		assertThat(reviews.run(List.of("--last", "2"))).extracting(Review::score)
			.usingElementComparator(BigDecimal::compareTo)
			.containsExactly(new BigDecimal("6.7"), new BigDecimal("8.1"));
	}

	@Test
	void saveRejectsInvalidReviews() {
		assertThatThrownBy(() -> save(review(monday.plusDays(1), "8.0", READING, SPANISH)))
			.hasRootCauseMessage("week_start must be a Monday: " + monday.plusDays(1));
		assertThatThrownBy(() -> save(review(monday.plusWeeks(1), "8.0", READING, SPANISH)))
			.hasMessage("the week of " + monday.plusWeeks(1) + " is not over yet: it ends on " + monday.plusWeeks(1).plusDays(6));
		assertThatThrownBy(() -> save(review(monday, "6.0", READING)))
			.hasMessage("every active area must be scored; missing: [spanish]");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH, READING.replace("reading", "gym"))))
			.hasMessage("not active areas: [gym]; active areas: [reading, spanish]");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH, SPANISH)))
			.hasMessage("each area may be scored only once: [reading, spanish, spanish]");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH.replace("\"score\": 9", "\"score\": 11"))))
			.hasRootCauseMessage("area score spanish: score must be from 0 to 10");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING.replace("\"score\": 6", "\"score\": -1"), SPANISH)))
			.hasRootCauseMessage("area score reading: score must be from 0 to 10");
		assertThatThrownBy(() -> save(review(monday, "10.5", READING, SPANISH)))
			.hasRootCauseMessage("score must be from 0 to 10 with at most one decimal: 10.5");
		assertThatThrownBy(() -> save(review(monday, "-1", READING, SPANISH)))
			.hasRootCauseMessage("score must be from 0 to 10 with at most one decimal: -1");
		assertThatThrownBy(() -> save(review(monday, "8.05", READING, SPANISH)))
			.hasRootCauseMessage("score must be from 0 to 10 with at most one decimal: 8.05");
		assertThatThrownBy(() -> save(review(monday, "7.8", READING, SPANISH)))
			.hasMessage("score 7.8 must be within 0.1 of the weighted average of the area scores, 8.00");
		assertThatThrownBy(() -> save(review(monday, "7.5", READING, SPANISH)))
			.as("the unweighted average")
			.hasMessageStartingWith("score 7.5 must be within 0.1");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH).replaceAll("\"summary\": \".*\"", "\"summary\": \"\"")))
			.hasRootCauseMessage("summary must be 1 to 1500 characters");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH)
			.replaceAll("\"summary\": \".*\"", "\"summary\": \"" + "x".repeat(1501) + "\"")))
			.hasRootCauseMessage("summary must be 1 to 1500 characters");
		assertThatThrownBy(() -> save(review(monday, "8.0", READING, SPANISH.replace("\"comment\"", "\"note\": \"\", \"comment\""))))
			.isInstanceOf(JacksonException.class)
			.hasMessageContaining("note");
		assertThat(reviews.run(List.of())).isEmpty();
	}

	@Test
	void reviewsReturnsTheLastNNewestFirst() {
		for (int weeks = 3; weeks >= 0; weeks--) {
			save(review(monday.minusWeeks(weeks), "8.0", READING, SPANISH));
		}

		assertThat(reviews.run(List.of())).extracting(Review::weekStart).containsExactly(monday);
		assertThat(reviews.run(List.of("--last", "3"))).extracting(Review::weekStart)
			.containsExactly(monday, monday.minusWeeks(1), monday.minusWeeks(2));
		assertThat(reviews.run(List.of("--last", "52"))).hasSize(4);
		assertThatThrownBy(() -> reviews.run(List.of("--last", "0")))
			.hasMessage("--last must be a whole number from 1 to 52: 0");
		assertThatThrownBy(() -> reviews.run(List.of("--last", "53")))
			.hasMessage("--last must be a whole number from 1 to 52: 53");
		assertThatThrownBy(() -> reviews.run(List.of("--last", "two")))
			.hasMessage("--last must be a whole number from 1 to 52: two");
	}

	@Test
	void gradeIsWorkedOutFromTheScore() {
		assertThat(List.of("10", "9.0", "8.9", "7.5", "7.4", "6.0", "5.9", "4.0", "3.9", "0"))
			.extracting(score -> new Review(monday, new BigDecimal(score), null, List.of(), "Summary").grade())
			.containsExactly("A", "A", "B", "B", "C", "C", "D", "D", "F", "F");
	}

	private static String review(LocalDate weekStart, String score, String... areaScores) {
		return """
				{ "week_start": "%s", "score": %s, "area_scores": [%s],
				  "summary": "Week of 9 to 15 November: 8.0, a B." }""".formatted(weekStart, score,
				String.join(",", areaScores));
	}

	private Review save(String json) {
		System.setIn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		return save.run(List.of());
	}

}
