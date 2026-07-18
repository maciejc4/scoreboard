package com.mc4.scoreboard;

/** Indicates that a match id does not refer to a currently live match. */
public final class MatchNotFoundException extends ScoreboardException {
    public MatchNotFoundException(MatchId matchId) {
        super("Match is not live: " + matchId);
    }

    public MatchNotFoundException(String message) {
        super(message);
    }
}
