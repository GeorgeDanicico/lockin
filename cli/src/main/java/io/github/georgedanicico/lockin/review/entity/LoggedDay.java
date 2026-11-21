package io.github.georgedanicico.lockin.review.entity;

import java.time.LocalDate;
import java.util.List;

/** A day with at least one note: the notes word for word, and the entries made from them. */
public record LoggedDay(LocalDate day, List<String> notes, List<LoggedEntry> entries) {
}
