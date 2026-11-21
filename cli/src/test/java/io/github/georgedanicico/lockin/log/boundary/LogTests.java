package io.github.georgedanicico.lockin.log.boundary;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.log.entity.Entry;
import io.github.georgedanicico.lockin.log.entity.Note;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class LogTests {

	private static final String NOTE = "Today i did the following: read 10 minutes, did my duolingo in spanish, "
			+ "read a few articles, spent time with my wife, 20 push-ups, fixed the sink";

	private static final InputStream STDIN = System.in;

	@Autowired
	Log log;

	@Autowired
	Day day;

	@Autowired
	EntryDelete entryDelete;

	@Autowired
	NoteDelete noteDelete;

	@Autowired
	AreaRepository areas;

	@Autowired
	LockinProperties properties;

	@Autowired
	JsonMapper json;

	LocalDate today;

	@BeforeEach
	void seedAreas() {
		today = properties.today();
		areas.save(List.of(area("reading", Unit.MINUTES), area("spanish", Unit.DAYS),
				area("relationship", Unit.SESSIONS), area("push-ups", Unit.COUNT)));
	}

	@AfterEach
	void restoreStdin() {
		System.setIn(STDIN);
	}

	@Test
	void logSavesTheNoteWordForWordWithItsEntries() {
		Note saved = log(today, NOTE, """
				{ "area": "reading", "description": "Read a book", "quantity": 10 },
				{ "area": "spanish", "description": "Duolingo lesson" },
				{ "area": "reading", "description": "Read a few articles" },
				{ "area": "relationship", "description": "Spent time with my wife" },
				{ "area": "push-ups", "description": "Push-ups", "quantity": 20 },
				{ "area": "other", "description": "Fixed the sink" },
				{ "description": "Called mum" },
				{ "area": null, "description": "Watered the plants" }""");

		assertThat(saved.id()).isNotNull();
		assertThat(saved.day()).isEqualTo(today);
		assertThat(saved.text()).isEqualTo(NOTE);
		assertThat(saved.entries()).extracting(Entry::id).doesNotContainNull().doesNotHaveDuplicates();
		assertThat(saved.entries()).extracting(Entry::area)
			.containsExactly("reading", "spanish", "reading", "relationship", "push-ups", null, null, null);
		assertThat(saved.entries().get(0).quantity()).isEqualByComparingTo("10");
		assertThat(saved.entries().get(1).quantity()).isNull();
		assertThat(day.run(List.of())).containsExactly(saved);
	}

	@Test
	void logSavesANoteWithNothingMapped() {
		Note saved = log(today, "Rest day", "");

		assertThat(saved.entries()).isEmpty();
		assertThat(day.run(List.of())).containsExactly(saved);
	}

	@Test
	void logPrintsSnakeCaseWithTheAreaSlug() {
		Note saved = log(today, "Fixed the sink", """
				{ "description": "Fixed the sink" }""");

		assertThat(json.writeValueAsString(saved)).isEqualTo("{\"id\":\"" + saved.id() + "\",\"day\":\"" + today
				+ "\",\"text\":\"Fixed the sink\",\"entries\":[{\"id\":\"" + saved.entries().get(0).id()
				+ "\",\"area\":null,\"description\":\"Fixed the sink\",\"quantity\":null}]}");
	}

	@Test
	void logRejectsAreasThatAreNotActive() {
		areas.remove(List.of("spanish"));

		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "gym", "description": "Gym" }"""))
			.hasMessage("not an active area: gym; active areas: [push-ups, reading, relationship], or other for none");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "spanish", "description": "Duolingo lesson" }"""))
			.hasMessageStartingWith("not an active area: spanish;");
		assertThat(day.run(List.of())).isEmpty();
	}

	@Test
	void logTakesQuantitiesOnlyForMinutesAndCountAreas() {
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "spanish", "description": "Duolingo lesson", "quantity": 1 }"""))
			.hasMessage("entry Duolingo lesson: area spanish counts days, so it takes no quantity");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "relationship", "description": "Dinner", "quantity": 2 }"""))
			.hasMessage("entry Dinner: area relationship counts sessions, so it takes no quantity");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "other", "description": "Fixed the sink", "quantity": 1 }"""))
			.hasMessage("entry Fixed the sink: entries with no area take no quantity");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "description": "Fixed the sink", "quantity": 1 }"""))
			.hasMessage("entry Fixed the sink: entries with no area take no quantity");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "reading", "description": "Read a book", "quantity": 0 }"""))
			.hasRootCauseMessage("entry Read a book: quantity must be greater than 0");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "push-ups", "description": "Push-ups", "quantity": -5 }"""))
			.hasRootCauseMessage("entry Push-ups: quantity must be greater than 0");
		assertThat(day.run(List.of())).isEmpty();
	}

	@Test
	void logChecksTheDay() {
		int max = properties.maxBackfillDays();
		LocalDate tooOld = today.minusDays(max + 1);

		assertThatThrownBy(() -> log("""
				{ "note": "Rest day", "entries": [] }""")).hasRootCauseMessage("day is required");
		assertThatThrownBy(() -> log(today.plusDays(1), "Rest day", ""))
			.hasMessage("day " + today.plusDays(1) + " is in the future; today is " + today);
		assertThatThrownBy(() -> log(tooOld, "Rest day", ""))
			.hasMessage("day " + tooOld + " is more than " + max + " days ago; pass --force to log it anyway");
		assertThatThrownBy(() -> log("""
				{ "day": "20-11-2026", "note": "Rest day", "entries": [] }""")).isInstanceOf(JacksonException.class);

		assertThat(log(today.minusDays(max), "Rest day", "").day()).isEqualTo(today.minusDays(max));
		assertThat(log(tooOld, "Rest day", "", "--force").day()).isEqualTo(tooOld);
	}

	@Test
	void logChecksTheNoteAndTheEntries() {
		assertThatThrownBy(() -> log(today, "", "")).hasRootCauseMessage("note must be 1 to 4000 characters");
		assertThatThrownBy(() -> log(today, "  ", "")).hasRootCauseMessage("note must be 1 to 4000 characters");
		assertThatThrownBy(() -> log(today, "x".repeat(4001), ""))
			.hasRootCauseMessage("note must be 1 to 4000 characters");
		assertThat(log(today, "x".repeat(4000), "").text()).hasSize(4000);
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "reading", "description": " " }""")).hasRootCauseMessage("entry description is required");
		assertThatThrownBy(() -> log(today, NOTE, """
				{ "area": "reading", "description": "Read", "minutes": 10 }"""))
			.isInstanceOf(JacksonException.class)
			.hasMessageContaining("minutes");
		assertThatThrownBy(() -> log("""
				{ "day": "%s", "note": "Rest day", "entries": [], "mood": "good" }""".formatted(today)))
			.isInstanceOf(JacksonException.class)
			.hasMessageContaining("mood");
	}

	@Test
	void dayShowsTheNotesOfOneDayOldestFirst() {
		LocalDate yesterday = today.minusDays(1);
		Note first = log(yesterday, "Read a bit", """
				{ "area": "reading", "description": "Read a book", "quantity": 15 }""");
		Note second = log(yesterday, "Spanish too", """
				{ "area": "spanish", "description": "Duolingo lesson" }""");
		log(today, "Rest day", "");

		assertThat(day.run(List.of("--day", yesterday.toString()))).containsExactly(first, second);
		assertThat(day.run(List.of("--day", today.minusDays(2).toString()))).isEmpty();
		assertThatThrownBy(() -> day.run(List.of("--day", "yesterday"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("--day must be YYYY-MM-DD: yesterday");
	}

	@Test
	void dayShowsTheSlugOfAnAreaThatIsNoLongerActive() {
		log(today, "Duolingo", """
				{ "area": "spanish", "description": "Duolingo lesson" }""");
		areas.remove(List.of("spanish"));

		assertThat(day.run(List.of()).get(0).entries()).extracting(Entry::area).containsExactly("spanish");
	}

	@Test
	void entryDeleteDeletesOneEntry() {
		Note saved = log(today, NOTE, """
				{ "area": "reading", "description": "Read a book", "quantity": 10 },
				{ "area": "other", "description": "Fixed the sink" }""");
		Entry reading = saved.entries().get(0);

		assertThat(entryDelete.run(List.of("--id", reading.id().toString()))).isEqualTo(reading);
		assertThat(day.run(List.of()).get(0).entries()).containsExactly(saved.entries().get(1));
		assertThat(entryDelete.run(List.of("--id", saved.entries().get(1).id().toString())).area()).isNull();
		assertThat(day.run(List.of())).extracting(Note::text).containsExactly(NOTE);
	}

	@Test
	void noteDeleteDeletesTheNoteAndItsEntries() {
		Note saved = log(today, NOTE, """
				{ "area": "reading", "description": "Read a book", "quantity": 10 },
				{ "area": "spanish", "description": "Duolingo lesson" }""");
		Note other = log(today, "Rest day", "");

		assertThat(noteDelete.run(List.of("--id", saved.id().toString()))).isEqualTo(saved);
		assertThat(day.run(List.of())).containsExactly(other);
		assertThatThrownBy(() -> entryDelete.run(List.of("--id", saved.entries().get(0).id().toString())))
			.as("the entries went with the note")
			.hasMessageStartingWith("no entry with id");
	}

	@Test
	void deleteNeedsAKnownId() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> entryDelete.run(List.of())).hasMessage("--id is required");
		assertThatThrownBy(() -> noteDelete.run(List.of())).hasMessage("--id is required");
		assertThatThrownBy(() -> entryDelete.run(List.of("--id", "abc"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> noteDelete.run(List.of("--id", "abc"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> entryDelete.run(List.of("--id", unknown.toString())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("no entry with id " + unknown);
		assertThatThrownBy(() -> noteDelete.run(List.of("--id", unknown.toString())))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("no note with id " + unknown);
	}

	private Note log(LocalDate day, String note, String entries, String... args) {
		return log("""
				{ "day": "%s", "note": %s, "entries": [%s] }""".formatted(day, json.writeValueAsString(note), entries),
				args);
	}

	private Note log(String input, String... args) {
		System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		return log.run(List.of(args));
	}

	private static Area area(String slug, Unit unit) {
		return new Area(slug, slug, unit, BigDecimal.TEN, null, "10/10: the target");
	}

}
