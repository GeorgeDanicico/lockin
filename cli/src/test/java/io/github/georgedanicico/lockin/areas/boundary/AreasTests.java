package io.github.georgedanicico.lockin.areas.boundary;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static io.github.georgedanicico.lockin.TestArguments.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class AreasTests {

	private static final String READING = """
			{ "slug": "reading", "name": "Reading", "unit": "minutes", "weekly_target": 150,
			  "rubric": "150 minutes over at least 4 days" }""";

	private static final String SPANISH = """
			{ "slug": "spanish", "name": "Spanish", "unit": "days", "weekly_target": 7, "weight": 2,
			  "rubric": "A lesson every day" }""";

	private static final InputStream STDIN = System.in;

	@Autowired
	AreasSave save;

	@Autowired
	AreasRemove remove;

	@Autowired
	AreasShow show;

	@AfterEach
	void restoreStdin() {
		System.setIn(STDIN);
	}

	@Test
	void saveCreatesSeveralAreasAtOnce() {
		save("[" + READING + "," + SPANISH + "]");

		var areas = show.run(args());
		assertThat(areas).extracting(Area::slug).containsExactly("reading", "spanish");
		assertThat(areas.get(0).unit()).isEqualTo(Unit.MINUTES);
		assertThat(areas.get(0).weight()).as("default weight").isEqualByComparingTo(BigDecimal.ONE);
		assertThat(areas.get(1).weight()).isEqualByComparingTo("2");
	}

	@Test
	void saveUpdatesOneAreaAndLeavesTheOthersAlone() {
		save("[" + READING + "," + SPANISH + "]");

		save("[" + READING.replace("150", "200") + "]");

		var areas = show.run(args());
		assertThat(areas).extracting(Area::slug).containsExactly("reading", "spanish");
		assertThat(areas.get(0).weeklyTarget()).isEqualByComparingTo("200");
	}

	@Test
	void removeSetsAreasInactiveAndSaveBringsThemBack() {
		save("[" + READING + "," + SPANISH + "]");

		assertThat(remove.run(args("spanish"))).containsExactly("spanish");
		assertThat(show.run(args())).extracting(Area::slug).containsExactly("reading");

		save("[" + SPANISH + "]");
		assertThat(show.run(args())).extracting(Area::slug).containsExactly("reading", "spanish");
	}

	@Test
	void removeChangesNothingWhenASlugIsNotActive() {
		save("[" + READING + "," + SPANISH + "]");

		assertThatThrownBy(() -> remove.run(args("spanish", "gym")))
			.hasMessage("not active areas: [gym]; active areas: [reading, spanish]");
		assertThatThrownBy(() -> remove.run(args())).hasMessage("at least one slug is required");
		assertThat(show.run(args())).extracting(Area::slug).containsExactly("reading", "spanish");
	}

	@Test
	void saveRejectsInvalidAreas() {
		assertThatThrownBy(() -> save("[]")).hasMessage("at least one area is required");
		assertThatThrownBy(() -> save("[" + READING + "," + READING + "]")).hasMessageContaining("only once");
		assertThatThrownBy(() -> save("[" + READING.replace("\"reading\"", "\"Reading\"") + "]"))
			.hasRootCauseMessage(
					"slug must be lowercase letters, digits and dashes, start with a letter, and not be 'other': Reading");
		assertThatThrownBy(() -> save("[" + READING.replace("minutes", "hours") + "]"))
			.isInstanceOf(JacksonException.class)
			.hasMessageContaining("hours");
		assertThatThrownBy(() -> save("[" + SPANISH.replace("\"weight\"", "\"wieght\"") + "]"))
			.as("a typo in an optional field is an error, not a silent default")
			.isInstanceOf(JacksonException.class)
			.hasMessageContaining("wieght");
	}

	private void save(String json) {
		System.setIn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		save.run(args());
	}

}
