package com.mc4.scoreboard;

import java.time.Instant;
import java.util.Objects;

/** Immutable point-in-time view of a live match. */
public record MatchSummary(
        MatchId matchId,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore,
        Instant startedAt) {

    public MatchSummary {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(homeTeam, "homeTeam");
        Objects.requireNonNull(awayTeam, "awayTeam");
        Objects.requireNonNull(startedAt, "startedAt");
        requireNonNegative("homeScore", homeScore);
        requireNonNegative("awayScore", awayScore);
    }

    public int total() {
        return homeScore + awayScore;
    }

    private static void requireNonNegative(String name, int score) {
        if (score < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + score);
        }
    }
}
