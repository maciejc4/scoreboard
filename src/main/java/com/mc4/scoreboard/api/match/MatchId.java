package com.mc4.scoreboard.api.match;

import lombok.EqualsAndHashCode;

/**
 * Opaque, immutable capability handle identifying a match.
 * Instances can only be created by code in this package.
 */
@EqualsAndHashCode
public final class MatchId {
    private final long value;

    public MatchId(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public String toString() {
        return "Match#" + value;
    }
}
