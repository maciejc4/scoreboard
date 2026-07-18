package com.example.scoreboard;

import lombok.EqualsAndHashCode;

/**
 * Opaque capability-handle identifying a match on the scoreboard.
 *
 * <p>Instances are created by the library only; the canonical constructor is
 * package-private so clients cannot forge ids. Equality is by value.
 */
@EqualsAndHashCode
public final class MatchId {

    private final long value;

    MatchId(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "MatchId(" + value + ")";
    }
}
