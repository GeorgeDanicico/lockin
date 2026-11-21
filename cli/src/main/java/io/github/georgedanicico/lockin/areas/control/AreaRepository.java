package io.github.georgedanicico.lockin.areas.control;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.areas.entity.Unit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AreaRepository {

	private final JdbcClient jdbc;

	AreaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Area> active() {
		return jdbc.sql("""
				select slug, name, unit, weekly_target, weight, rubric
				from lockin.areas
				where is_active
				order by slug
				""").query(AreaRepository::area).list();
	}

	/**
	 * Creates each area, or updates the one with the same slug and makes it active again. Other
	 * areas are not touched.
	 */
	@Transactional
	public void save(List<Area> areas) {
		require(!areas.isEmpty(), "at least one area is required");
		List<String> slugs = areas.stream().map(Area::slug).toList();
		require(new HashSet<>(slugs).size() == slugs.size(), "each slug may appear only once: " + slugs);
		for (Area area : areas) {
			jdbc.sql("""
					insert into lockin.areas (slug, name, unit, weekly_target, weight, rubric)
					values (:slug, :name, :unit, :weeklyTarget, :weight, :rubric)
					on conflict (slug) do update set
					  name = excluded.name, unit = excluded.unit, weekly_target = excluded.weekly_target,
					  weight = excluded.weight, rubric = excluded.rubric, is_active = true
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

	/** Sets the areas inactive. Their entries keep their area. Every slug must be an active area. */
	@Transactional
	public void remove(List<String> slugs) {
		require(!slugs.isEmpty(), "at least one slug is required");
		List<String> active = active().stream().map(Area::slug).toList();
		List<String> unknown = slugs.stream().filter(slug -> !active.contains(slug)).toList();
		require(unknown.isEmpty(), "not active areas: " + unknown + "; active areas: " + active);
		jdbc.sql("update lockin.areas set is_active = false where slug in (:slugs)").param("slugs", slugs).update();
	}

	private static Area area(ResultSet row, int number) throws SQLException {
		return new Area(row.getString("slug"), row.getString("name"), Unit.of(row.getString("unit")),
				row.getBigDecimal("weekly_target"), row.getBigDecimal("weight"), row.getString("rubric"));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
