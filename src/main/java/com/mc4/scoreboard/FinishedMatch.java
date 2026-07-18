package com.mc4.scoreboard;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable record of a completed match. */
public record FinishedMatch(
        MatchId matchId,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        Instant startedAt,
        Instant finishedAt) {

    public FinishedMatch {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(homeTeam, "homeTeam");
        Objects.requireNonNull(awayTeam, "awayTeam");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        requireNonNegative("homeScore", homeScore);
        requireNonNegative("awayScore", awayScore);
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    private static void requireNonNegative(String name, int score) {
        if (score < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + score);
        }
    }
}
