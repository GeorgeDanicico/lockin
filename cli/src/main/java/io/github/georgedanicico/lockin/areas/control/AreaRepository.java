package io.github.georgedanicico.lockin.areas.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.InactiveArea;
import io.github.georgedanicico.lockin.areas.entity.Unit;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AreaRepository {

	private final JdbcClient jdbc;

	private final LockinProperties properties;

	AreaRepository(JdbcClient jdbc, LockinProperties properties) {
		this.jdbc = jdbc;
		this.properties = properties;
	}

	public List<Area> active() {
		return jdbc.sql("""
				select slug, name, unit, weekly_target, weight, rubric
				from lockin.areas
				where is_active
				order by slug
				""").query(AreaRepository::area).list();
	}

	/** The inactive areas, most recently deactivated first, with the day each was deactivated. */
	public List<InactiveArea> inactive() {
		return jdbc.sql("""
				select slug, name, unit, weekly_target, weight, rubric,
				       (deactivated_at at time zone :zone)::date as deactivated_on
				from lockin.areas
				where not is_active
				order by deactivated_at desc nulls last, slug
				""").param("zone", properties.zone().getId()).query(AreaRepository::inactiveArea).list();
	}

	/**
	 * Creates each area, or updates the one with the same slug and makes it active again. Other
	 * areas are not touched.
	 */
	@Transactional
	public void save(List<Area> areas) {
		require(!areas.isEmpty(), "at least one area is required");
		var slugs = areas.stream().map(Area::slug).toList();
		require(new HashSet<>(slugs).size() == slugs.size(), "each slug may appear only once: " + slugs);
		for (var area : areas) {
			jdbc.sql("""
					insert into lockin.areas (slug, name, unit, weekly_target, weight, rubric)
					values (:slug, :name, :unit, :weeklyTarget, :weight, :rubric)
					on conflict (slug) do update set
					  name = excluded.name, unit = excluded.unit, weekly_target = excluded.weekly_target,
					  weight = excluded.weight, rubric = excluded.rubric, is_active = true, deactivated_at = null
					""")
				.param("slug", area.slug())
				.param("name", area.name())
				.param("unit", area.unit().value())
				.param("weeklyTarget", area.weeklyTarget())
				.param("weight", area.weight())
				.param("rubric", area.rubric())
				.update();
		}
	}

	/**
	 * Sets the areas inactive and notes when. Their entries keep their area. Every slug must be an
	 * active area.
	 */
	@Transactional
	public void remove(List<String> slugs) {
		require(!slugs.isEmpty(), "at least one slug is required");
		var active = active().stream().map(Area::slug).toList();
		var unknown = slugs.stream().filter(slug -> !active.contains(slug)).toList();
		require(unknown.isEmpty(), "not active areas: " + unknown + "; active areas: " + active);
		jdbc.sql("update lockin.areas set is_active = false, deactivated_at = now() where slug in (:slugs)")
			.param("slugs", slugs)
			.update();
	}

	/**
	 * Makes inactive areas active again, unchanged, and returns them. Every slug must be an inactive
	 * area.
	 */
	@Transactional
	public List<Area> activate(List<String> slugs) {
		require(!slugs.isEmpty(), "at least one slug is required");
		var inactive = inactive().stream().map(InactiveArea::slug).toList();
		var unknown = slugs.stream().filter(slug -> !inactive.contains(slug)).toList();
		require(unknown.isEmpty(), "not inactive areas: " + unknown + "; inactive areas: " + inactive);
		jdbc.sql("update lockin.areas set is_active = true, deactivated_at = null where slug in (:slugs)")
			.param("slugs", slugs)
			.update();
		return active().stream().filter(area -> slugs.contains(area.slug())).toList();
	}

	private static Area area(ResultSet row, int number) throws SQLException {
		return new Area(row.getString("slug"), row.getString("name"), Unit.of(row.getString("unit")),
				row.getBigDecimal("weekly_target"), row.getBigDecimal("weight"), row.getString("rubric"));
	}

	private static InactiveArea inactiveArea(ResultSet row, int number) throws SQLException {
		return new InactiveArea(row.getString("slug"), row.getString("name"), Unit.of(row.getString("unit")),
				row.getBigDecimal("weekly_target"), row.getBigDecimal("weight"), row.getString("rubric"),
				row.getObject("deactivated_on", LocalDate.class));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
