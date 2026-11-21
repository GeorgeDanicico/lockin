package io.github.georgedanicico.lockin.log.entity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A saved note, word for word, with its entries. */
public record Note(UUID id, LocalDate day, String text, List<Entry> entries) {
}
