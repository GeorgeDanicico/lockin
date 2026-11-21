package io.github.georgedanicico.lockin.review.control;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import io.github.georgedanicico.lockin.areas.control.AreaRepository;
import io.github.georgedanicico.lockin.areas.entity.Area;
import io.github.georgedanicico.lockin.cli.control.LockinProperties;
import io.github.georgedanicico.lockin.review.entity.AreaScore;
import io.github.georgedanicico.lockin.review.entity.Review;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ReviewRepository {

	private static final String SELECT = """
			select week_start, score, area_scores, summary
			from lockin.reviews
			""";

	private static final BigDecimal TOLERANCE = new BigDecimal("0.1");

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final AreaRepository areas;

	private final LockinProperties properties;

	ReviewRepository(JdbcClient jdbc, JsonMapper json, AreaRepository areas, LockinProperties properties) {
		this.jdbc = jdbc;
		this.json = json;
		this.areas = areas;
		this.properties = properties;
	}

	/**
	 * Saves the review of a completed week, replacing the one already saved for that week. Every
	 * active area must be scored once, and the score must be the weighted average of the area scores.
	 */
	@Transactional
	public Review save(Review review) {
		var sunday = review.weekStart().plusDays(6);
		require(sunday.isBefore(properties.today()),
				"the week of " + review.weekStart() + " is not over yet: it ends on " + sunday);
		var active = areas.active();
		require(!active.isEmpty(), "there are no active areas to review");
		var weights = active.stream().collect(Collectors.toMap(Area::slug, Area::weight));
		var slugs = review.areaScores().stream().map(AreaScore::slug).toList();
		require(new HashSet<>(slugs).size() == slugs.size(), "each area may be scored only once: " + slugs);
		var unknown = slugs.stream().filter(slug -> !weights.containsKey(slug)).toList();
		require(unknown.isEmpty(), "not active areas: " + unknown + "; active areas: " + active.stream().map(Area::slug).toList());
		var missing = active.stream().map(Area::slug).filter(slug -> !slugs.contains(slug)).toList();
		require(missing.isEmpty(), "every active area must be scored; missing: " + missing);
		var weighted = BigDecimal.ZERO;
		var total = BigDecimal.ZERO;
		for (var area : review.areaScores()) {
			weighted = weighted.add(weights.get(area.slug()).multiply(area.score()));
			total = total.add(weights.get(area.slug()));
		}
		var average = weighted.divide(total, MathContext.DECIMAL64);
		require(review.score().subtract(average).abs().compareTo(TOLERANCE) <= 0,
				"score " + review.score() + " must be within 0.1 of the weighted average of the area scores, "
						+ average.setScale(2, RoundingMode.HALF_UP));
		return jdbc.sql("""
				insert into lockin.reviews (week_start, score, area_scores, summary)
				values (:weekStart, :score, :areaScores::jsonb, :summary)
				on conflict (week_start) do update set
				  score = excluded.score, area_scores = excluded.area_scores, summary = excluded.summary,
				  updated_at = now()
				returning week_start, score, area_scores, summary
				""")
			.param("weekStart", review.weekStart())
			.param("score", review.score())
			.param("areaScores", json.writeValueAsString(review.areaScores()))
			.param("summary", review.summary())
			.query(this::review)
			.single();
	}

	/** The last reviews, newest first. */
	public List<Review> last(int count) {
		return jdbc.sql(SELECT + "order by week_start desc limit :count")
			.param("count", count)
			.query(this::review)
			.list();
	}

	/** The last reviews of weeks before the given Monday, newest first. */
	public List<Review> before(LocalDate weekStart, int count) {
		return jdbc.sql(SELECT + "where week_start < :weekStart order by week_start desc limit :count")
			.param("weekStart", weekStart)
			.param("count", count)
			.query(this::review)
			.list();
	}

	private Review review(ResultSet row, int number) throws SQLException {
		return new Review(row.getObject("week_start", LocalDate.class), row.getBigDecimal("score"), null,
				json.readValue(row.getString("area_scores"), new TypeReference<List<AreaScore>>() {
				}), row.getString("summary"));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
