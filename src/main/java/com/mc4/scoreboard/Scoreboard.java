package com.mc4.scoreboard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe scoreboard API for live football matches.
 */
public interface Scoreboard {

    static Scoreboard inMemory() {
        return inMemory(ScoreboardConfig.builder().build());
    }

    static Scoreboard inMemory(ScoreboardConfig config) {
        Objects.requireNonNull(config, "config");
        throw new UnsupportedOperationException("In-memory scoreboard is not implemented yet");
    }

    MatchId startMatch(String homeTeam, String awayTeam);

    void recordHomeGoal(MatchId matchId);

    void recordAwayGoal(MatchId matchId);

    void correctHomeGoal(MatchId matchId);

    void correctAwayGoal(MatchId matchId);

    void finishMatch(MatchId matchId);

    Optional<MatchSummary> getMatch(MatchId matchId);

    List<MatchSummary> getSummary();

    List<FinishedMatch> getHistory();

    void clearHistory();
}
