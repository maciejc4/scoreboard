package com.mc4.scoreboard.core;

import com.mc4.scoreboard.api.Scoreboard;
import com.mc4.scoreboard.api.config.ScoreboardConfig;
import com.mc4.scoreboard.api.match.FinishedMatch;
import com.mc4.scoreboard.api.match.MatchId;
import com.mc4.scoreboard.api.match.MatchNotFoundException;
import com.mc4.scoreboard.api.match.MatchSummary;
import com.mc4.scoreboard.core.entity.Match;
import lombok.NonNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory {@link Scoreboard}.
 *
 * <p>Dummy scaffolding: the configuration is captured, but every operation
 * throws {@link UnsupportedOperationException} until the behaviour is implemented.
 */
public final class InMemoryScoreboard implements Scoreboard {

    private static final Comparator<Match> SUMMARY_ORDER = Comparator
            .comparingInt(Match::totalScore).reversed()
            .thenComparing(Comparator.comparingLong(Match::startSequence).reversed());

    private final ScoreboardConfig config;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private long matchCounter;
    Map<MatchId, Match> live = new HashMap<>();
    LinkedList<FinishedMatch> history = new LinkedList<>();

    public InMemoryScoreboard(ScoreboardConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public MatchId startMatch(@NonNull String homeTeam, @NonNull String awayTeam) {
        String normalizedHomeTeam = homeTeam.trim().toUpperCase(Locale.ROOT);
        String normalizedAwayTeam = awayTeam.trim().toUpperCase(Locale.ROOT);
        if (normalizedHomeTeam.equals(normalizedAwayTeam)) {
            throw new IllegalArgumentException("Home and Away teams cannot be the same");
        }

        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            matchCounter++;
            MatchId matchId = new MatchId(matchCounter);
            live.put(matchId, new Match(matchId, homeTeam, awayTeam, Instant.now()));
            return matchId;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void recordHomeGoal(MatchId matchId) {
        Match match = liveMatch(matchId);
        match.scoreHome();
    }

    @Override
    public void recordAwayGoal(MatchId matchId) {
        Match match = liveMatch(matchId);
        match.scoreAway();
    }

    @Override
    public void correctHomeGoal(MatchId matchId) {
        Match match = liveMatch(matchId);
        match.correctHome();
    }

    @Override
    public void correctAwayGoal(MatchId matchId) {
        Match match = liveMatch(matchId);
        match.correctAway();
    }

    @Override
    public void finishMatch(MatchId matchId) {
        Match match = liveMatch(matchId);
        FinishedMatch finished = match.toFinished(Instant.now());
        history.add(finished);
        live.remove(matchId);
    }


    @Override
    public Optional<MatchSummary> getMatch(MatchId matchId) {
        return Optional.ofNullable(live.get(matchId)).map(Match::toSummary);
    }

    private Match liveMatch(MatchId matchId) {
        Objects.requireNonNull(matchId, "matchId must not be null");
        Match match = live.get(matchId);
        if (match == null) {
            throw new MatchNotFoundException(matchId);
        }
        return match;
    }

    @Override
    public List<MatchSummary> getSummary() {
        return List.copyOf(live.values()).stream()
                .sorted(SUMMARY_ORDER)
                .map(Match::toSummary)
                .toList();

    }


    @Override
    public List<FinishedMatch> getHistory() {
        return List.copyOf(history).stream().toList();
    }

    @Override
    public void clearHistory() {
        history.clear();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Not implemented yet");
    }
}
