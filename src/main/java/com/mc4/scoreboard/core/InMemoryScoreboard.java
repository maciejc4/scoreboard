package com.mc4.scoreboard.core;

import com.mc4.scoreboard.api.Scoreboard;
import com.mc4.scoreboard.api.config.ScoreboardConfig;
import com.mc4.scoreboard.api.match.FinishedMatch;
import com.mc4.scoreboard.api.match.MatchId;
import com.mc4.scoreboard.api.match.MatchNotFoundException;
import com.mc4.scoreboard.api.match.MatchSummary;
import com.mc4.scoreboard.core.entity.Match;
import com.mc4.scoreboard.core.entity.TeamName;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

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
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    private long matchCounter;
    Map<MatchId, Match> live = new HashMap<>();
    LinkedList<FinishedMatch> history = new LinkedList<>();

    public InMemoryScoreboard(ScoreboardConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        TeamName homeTeamName = new TeamName(homeTeam);
        TeamName awayTeamName = new TeamName(awayTeam);
        if (homeTeamName.equals(awayTeamName)) {
            throw new IllegalArgumentException("Home and Away teams cannot be the same");
        }

        return withWriteLock(() -> {
            matchCounter++;
            MatchId matchId = new MatchId(matchCounter);
            live.put(matchId, new Match(matchId, homeTeamName, awayTeamName, Instant.now()));
            return matchId;
        });
    }

    @Override
    public void recordHomeGoal(MatchId matchId) {
        withWriteLock(() -> {
            Match match = liveMatch(matchId);
            match.scoreHome();
        });
    }

    @Override
    public void recordAwayGoal(MatchId matchId) {
        withWriteLock(() -> {
            Match match = liveMatch(matchId);
            match.scoreAway();
        });
    }

    @Override
    public void correctHomeGoal(MatchId matchId) {
        withWriteLock(() -> {
            Match match = liveMatch(matchId);
            match.correctHome();
        });
    }

    @Override
    public void correctAwayGoal(MatchId matchId) {
        withWriteLock(() -> {
            Match match = liveMatch(matchId);
            match.correctAway();
        });
    }

    @Override
    public void finishMatch(MatchId matchId) {
        withWriteLock(() -> {
            Match match = liveMatch(matchId);
            FinishedMatch finished = match.toFinished(Instant.now());
            history.add(finished);
            live.remove(matchId);
        });
    }


    @Override
    public Optional<MatchSummary> getMatch(MatchId matchId) {
        return withReadLock(() -> Optional.ofNullable(live.get(matchId)).map(Match::toSummary));
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
        return withReadLock(() -> List.copyOf(live.values()).stream()
                .sorted(SUMMARY_ORDER)
                .map(Match::toSummary)
                .toList());

    }


    @Override
    public List<FinishedMatch> getHistory() {
        return withReadLock(() -> List.copyOf(history).stream().toList());
    }

    @Override
    public void clearHistory() {
        withWriteLock(history::clear);
    }

    private <T> T withReadLock(Supplier<T> operation) {
        return withLock(readLock, operation);
    }

    private <T> T withWriteLock(Supplier<T> operation) {
        return withLock(writeLock, operation);
    }

    private void withWriteLock(Runnable operation) {
        withLock(writeLock, () -> {
            operation.run();
            return null;
        });
    }

    private static <T> T withLock(Lock lock, Supplier<T> operation) {
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Not implemented yet");
    }
}
