package io.github.georgedanicico.lockin.log.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What {@code lockin log} reads: the note as I said it, and the entries Claude mapped it to.
 *
 * @param entries may be empty: a note with nothing mapped
 */
public record LogRequest(LocalDate day, String note, List<Item> entries) {

	public LogRequest {
		require(day != null, "day is required");
		require(note != null && !note.isBlank() && note.codePointCount(0, note.length()) <= 4000,
				"note must be 1 to 4000 characters");
		entries = (entries != null) ? entries : List.of();
	}

	/**
	 * One entry to save.
	 *
	 * @param area an active area's slug; null or "other" for none
	 * @param quantity in the area's unit; only for minutes and count areas
	 */
	public record Item(String area, String description, BigDecimal quantity) {

		public Item {
			area = "other".equals(area) ? null : area;
			require(description != null && !description.isBlank(), "entry description is required");
			require(quantity == null || quantity.signum() > 0, "entry " + description + ": quantity must be greater than 0");
		}

	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
