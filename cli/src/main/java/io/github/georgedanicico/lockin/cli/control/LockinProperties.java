package io.github.georgedanicico.lockin.cli.control;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param zone my timezone ({@code LOCKIN_TZ}, default {@code Europe/Bucharest}); "today" and "last week" are in this zone
 * @param maxBackfillDays how many days back a day may be logged without {@code --force}
 */
@ConfigurationProperties("lockin")
public record LockinProperties(ZoneId zone, int maxBackfillDays) {

	public LockinProperties {
		if (zone == null) {
			throw new IllegalArgumentException("lockin.zone (LOCKIN_TZ) is required");
		}
		if (maxBackfillDays < 0) {
			throw new IllegalArgumentException("lockin.max-backfill-days must be 0 or more");
		}
	}

	public LocalDate today() {
		return LocalDate.now(zone);
	}

}
