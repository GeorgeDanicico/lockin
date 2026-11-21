package io.github.georgedanicico.lockin.log.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.log.entity.Entry;
import io.github.georgedanicico.lockin.log.entity.LogRequest;
import io.github.georgedanicico.lockin.log.entity.Note;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LogRepository {

	private final JdbcClient jdbc;

	private final AreaRepository areas;

	private final LockinProperties properties;

	LogRepository(JdbcClient jdbc, AreaRepository areas, LockinProperties properties) {
		this.jdbc = jdbc;
		this.areas = areas;
		this.properties = properties;
	}

	/**
	 * Saves the note and its entries. The day may not be in the future, nor older than
	 * {@code max-backfill-days} unless {@code force}. Each entry's area must be active, and only
	 * minutes and count areas take a quantity.
	 */
	@Transactional
	public Note save(LogRequest request, boolean force) {
		var today = properties.today();
		var maxBackfillDays = properties.maxBackfillDays();
		require(!request.day().isAfter(today), "day " + request.day() + " is in the future; today is " + today);
		require(force || !request.day().isBefore(today.minusDays(maxBackfillDays)),
				"day " + request.day() + " is more than " + maxBackfillDays + " days ago; pass --force to log it anyway");
		var active = areas.active();
		var units = active.stream().collect(Collectors.toMap(Area::slug, Area::unit));
		for (var item : request.entries()) {
			if (item.area() == null) {
				require(item.quantity() == null, "entry " + item.description() + ": entries with no area take no quantity");
				continue;
			}
			var unit = units.get(item.area());
			require(unit != null, "not an active area: " + item.area() + "; active areas: "
					+ active.stream().map(Area::slug).toList() + ", or other for none");
			require(item.quantity() == null || unit == Unit.MINUTES || unit == Unit.COUNT, "entry " + item.description()
					+ ": area " + item.area() + " counts " + unit.value() + ", so it takes no quantity");
		}

		// now() is the same for the whole transaction; clock_timestamp() keeps the input order.
		var noteId = jdbc.sql("""
				insert into lockin.daily_notes (day, raw_text, created_at)
				values (:day, :text, clock_timestamp())
				returning id
				""").param("day", request.day()).param("text", request.note()).query(UUID.class).single();
		var entries = new ArrayList<Entry>();
		for (var item : request.entries()) {
			var entryId = jdbc.sql("""
					insert into lockin.entries (note_id, area_id, description, quantity, created_at)
					values (:noteId, (select id from lockin.areas where slug = :area), :description, :quantity,
					        clock_timestamp())
					returning id
					""")
				.param("noteId", noteId)
				.param("area", item.area())
				.param("description", item.description())
				.param("quantity", item.quantity())
				.query(UUID.class)
				.single();
			entries.add(new Entry(entryId, item.area(), item.description(), item.quantity()));
		}
		return new Note(noteId, request.day(), request.note(), entries);
	}

	/** The day's notes, oldest first, each with its entries. */
	public List<Note> day(LocalDate day) {
		return jdbc.sql("select id from lockin.daily_notes where day = :day order by created_at, id")
			.param("day", day)
			.query(UUID.class)
			.list()
			.stream()
			.map(this::note)
			.toList();
	}

	public Entry deleteEntry(UUID id) {
		return jdbc.sql("""
				delete from lockin.entries e
				where e.id = :id
				returning e.id, (select slug from lockin.areas a where a.id = e.area_id) as area, e.description, e.quantity
				""")
			.param("id", id)
			.query(LogRepository::entry)
			.optional()
			.orElseThrow(() -> new IllegalArgumentException("no entry with id " + id));
	}

	/** Deletes the note and, through the foreign key, its entries. */
	@Transactional
	public Note deleteNote(UUID id) {
		var note = note(id);
		jdbc.sql("delete from lockin.daily_notes where id = :id").param("id", id).update();
		return note;
	}

	private Note note(UUID id) {
		var entries = jdbc.sql("""
				select e.id, a.slug as area, e.description, e.quantity
				from lockin.entries e
				left join lockin.areas a on a.id = e.area_id
				where e.note_id = :id
				order by e.created_at, e.id
				""").param("id", id).query(LogRepository::entry).list();
		return jdbc.sql("select id, day, raw_text from lockin.daily_notes where id = :id")
			.param("id", id)
			.query((row, number) -> new Note(row.getObject("id", UUID.class), row.getObject("day", LocalDate.class),
					row.getString("raw_text"), entries))
			.optional()
			.orElseThrow(() -> new IllegalArgumentException("no note with id " + id));
	}

	private static Entry entry(ResultSet row, int number) throws SQLException {
		return new Entry(row.getObject("id", UUID.class), row.getString("area"), row.getString("description"),
				row.getBigDecimal("quantity"));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
