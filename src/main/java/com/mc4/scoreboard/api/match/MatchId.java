package com.mc4.scoreboard.api.match;

/**
 * Opaque, immutable capability handle identifying a match.
 * Instances can only be created by code in this package.
 */
public final class MatchId {
    private final long value;

    MatchId(long value) {
        this.value = value;
    }

    long value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MatchId matchId && value == matchId.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "Match#" + value;
    }
}
