package io.github.georgedanicico.lockin.areas.boundary;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.georgedanicico.lockin.TestDatabase;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.InactiveArea;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
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

	@Autowired
	AreasActivate activate;

	@Autowired
	LockinProperties properties;

	@AfterEach
	void restoreStdin() {
		System.setIn(STDIN);
	}

	@Test
	void saveCreatesSeveralAreasAtOnce() {
		save("[" + READING + "," + SPANISH + "]");

		var areas = active();
		assertThat(areas).extracting(Area::slug).containsExactly("reading", "spanish");
		assertThat(areas.get(0).unit()).isEqualTo(Unit.MINUTES);
		assertThat(areas.get(0).weight()).as("default weight").isEqualByComparingTo(BigDecimal.ONE);
		assertThat(areas.get(1).weight()).isEqualByComparingTo("2");
	}

	@Test
	void saveUpdatesOneAreaAndLeavesTheOthersAlone() {
		save("[" + READING + "," + SPANISH + "]");

		save("[" + READING.replace("150", "200") + "]");

		var areas = active();
		assertThat(areas).extracting(Area::slug).containsExactly("reading", "spanish");
		assertThat(areas.get(0).weeklyTarget()).isEqualByComparingTo("200");
	}

	@Test
	void removeSetsAreasInactiveAndSaveBringsThemBack() {
		save("[" + READING + "," + SPANISH + "]");

		assertThat(remove.run(args("spanish"))).containsExactly("spanish");
		assertThat(active()).extracting(Area::slug).containsExactly("reading");

		save("[" + SPANISH + "]");
		assertThat(active()).extracting(Area::slug).containsExactly("reading", "spanish");
	}

	@Test
	void removeChangesNothingWhenASlugIsNotActive() {
		save("[" + READING + "," + SPANISH + "]");

		assertThatThrownBy(() -> remove.run(args("spanish", "gym")))
			.hasMessage("not active areas: [gym]; active areas: [reading, spanish]");
		assertThatThrownBy(() -> remove.run(args())).hasMessage("at least one slug is required");
		assertThat(active()).extracting(Area::slug).containsExactly("reading", "spanish");
	}

	@Test
	void showInactiveListsRemovedAreasWithTheDayTheyWereDeactivated() {
		save("[" + READING + "," + SPANISH + "]");
		assertThat(inactive()).isEmpty();

		remove.run(args("spanish"));

		var inactive = inactive();
		assertThat(inactive).hasSize(1);
		var spanish = inactive.getFirst();
		assertThat(spanish.slug()).isEqualTo("spanish");
		assertThat(spanish.rubric()).isEqualTo("A lesson every day");
		assertThat(spanish.weight()).isEqualByComparingTo("2");
		assertThat(spanish.deactivatedOn()).isEqualTo(properties.today());
	}

	@Test
	void activateBringsAreasBackUnchanged() {
		save("[" + READING + "," + SPANISH + "]");
		remove.run(args("reading", "spanish"));

		var activated = activate.run(args("spanish"));

		assertThat(activated).extracting(Area::slug).containsExactly("spanish");
		assertThat(activated.getFirst().weeklyTarget()).isEqualByComparingTo("7");
		assertThat(activated.getFirst().rubric()).isEqualTo("A lesson every day");
		assertThat(active()).extracting(Area::slug).containsExactly("spanish");
		assertThat(inactive()).extracting(InactiveArea::slug)
			.containsExactly("reading");
	}

	@Test
	void saveOfAnInactiveAreaClearsItsDeactivatedDay() {
		save("[" + READING + "]");
		remove.run(args("reading"));

		save("[" + READING + "]");

		assertThat(inactive()).isEmpty();
	}

	@Test
	void activateChangesNothingWhenASlugIsNotInactive() {
		save("[" + READING + "," + SPANISH + "]");
		remove.run(args("spanish"));

		assertThatThrownBy(() -> activate.run(args("spanish", "reading", "gym")))
			.hasMessage("not inactive areas: [reading, gym]; inactive areas: [spanish]");
		assertThatThrownBy(() -> activate.run(args())).hasMessage("at least one slug is required");
		assertThat(active()).extracting(Area::slug).containsExactly("reading");
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

	@SuppressWarnings("unchecked")
	private List<Area> active() {
		return (List<Area>) show.run(args());
	}

	@SuppressWarnings("unchecked")
	private List<InactiveArea> inactive() {
		return (List<InactiveArea>) show.run(args("--inactive"));
	}

	private void save(String json) {
		System.setIn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		save.run(args());
	}

}
