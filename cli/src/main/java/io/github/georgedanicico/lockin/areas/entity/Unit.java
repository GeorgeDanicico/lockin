package io.github.georgedanicico.lockin.areas.entity;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** How an area's weekly actual is counted. Written in lowercase, in JSON and in the database. */
public enum Unit {

	/** Sum of the entries' minutes. */
	MINUTES,
	/** Sum of the entries' counts. */
	COUNT,
	/** Number of entries. */
	SESSIONS,
	/** Number of distinct days with an entry. */
	DAYS;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Unit of(String value) {
		return valueOf(value.toUpperCase(Locale.ROOT));
	}

}
