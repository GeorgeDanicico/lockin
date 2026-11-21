package io.github.georgedanicico.lockin.review.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.review.entity.AreaTotal;
import io.github.georgedanicico.lockin.review.entity.LoggedDay;
import io.github.georgedanicico.lockin.review.entity.LoggedEntry;
import io.github.georgedanicico.lockin.review.entity.WeekReport;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WeekRepository {

	private final JdbcClient jdbc;

	private final ReviewRepository reviews;

	WeekRepository(JdbcClient jdbc, ReviewRepository reviews) {
		this.jdbc = jdbc;
		this.reviews = reviews;
	}

	/** The week that starts on the given Monday, with the last 4 reviews before it. */
	@Transactional(readOnly = true) // one connection for all the queries, as there is no pool
	public WeekReport report(LocalDate weekStart) {
		var weekEnd = weekStart.plusDays(6);
		var totals = jdbc.sql("""
				select a.slug, a.name, a.unit, a.weekly_target, a.weight, a.rubric,
				       case a.unit
				         when 'sessions' then count(e.id)
				         when 'days' then count(distinct n.day)
				         else coalesce(sum(e.quantity), 0)
				       end as actual
				from lockin.areas a
				left join (lockin.entries e join lockin.daily_notes n
				           on n.id = e.note_id and n.day between :weekStart and :weekEnd)
				  on e.area_id = a.id
				where a.is_active
				group by a.id
				order by a.slug
				""")
			.param("weekStart", weekStart)
			.param("weekEnd", weekEnd)
			.query(WeekRepository::total)
			.list();
		var notes = jdbc.sql("""
				select day, raw_text
				from lockin.daily_notes
				where day between :weekStart and :weekEnd
				order by day, created_at
				""")
			.param("weekStart", weekStart)
			.param("weekEnd", weekEnd)
			.query((row, number) -> Map.entry(row.getObject("day", LocalDate.class), row.getString("raw_text")))
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, TreeMap::new,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		var entries = jdbc.sql("""
				select n.day, a.slug, e.description, e.quantity
				from lockin.entries e
				join lockin.daily_notes n on n.id = e.note_id
				left join lockin.areas a on a.id = e.area_id
				where n.day between :weekStart and :weekEnd
				order by n.day, n.created_at, e.created_at
				""")
			.param("weekStart", weekStart)
			.param("weekEnd", weekEnd)
			.query((row, number) -> Map.entry(row.getObject("day", LocalDate.class),
					new LoggedEntry(row.getString("slug"), row.getString("description"), row.getBigDecimal("quantity"))))
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		var days = notes.entrySet()
			.stream()
			.map(day -> new LoggedDay(day.getKey(), day.getValue(), entries.getOrDefault(day.getKey(), List.of())))
			.toList();
		var unlogged = weekStart.datesUntil(weekEnd.plusDays(1)).filter(day -> !notes.containsKey(day)).toList();
		return new WeekReport(weekStart, weekEnd, totals, days, unlogged, reviews.before(weekStart, 4));
	}

	private static AreaTotal total(ResultSet row, int number) throws SQLException {
		return new AreaTotal(row.getString("slug"), row.getString("name"), Unit.of(row.getString("unit")),
				row.getBigDecimal("weekly_target"), row.getBigDecimal("weight"), row.getString("rubric"),
				row.getBigDecimal("actual"));
	}

}
