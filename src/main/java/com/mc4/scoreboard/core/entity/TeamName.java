package com.mc4.scoreboard.core.entity;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Locale;
import java.util.Objects;

/** Immutable team name whose identity is its normalized form. */
@Value
@Accessors(fluent = true)
@EqualsAndHashCode(of = "normalized")
public class TeamName {

    String original;
    String normalized;

    public TeamName(String value) {
        String trimmed = Objects.requireNonNull(value, "team name must not be null").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Team name must not be blank");
        }

        this.original = trimmed;
        this.normalized = trimmed.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return original;
    }
}
