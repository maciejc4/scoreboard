package com.mc4.scoreboard.core.entity;

import com.mc4.scoreboard.api.match.FinishedMatch;
import com.mc4.scoreboard.api.match.MatchId;
import com.mc4.scoreboard.api.match.MatchSummary;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Match {
    private final MatchId id;
    private final String homeTeamName;
    private final String awayTeamName;
    private int homeScore;
    private int awayScore;
    private Status status;
    private final Instant startTime;

    public Match(MatchId id, String homeTeamName, String awayTeamName, Instant startTime) {
        this.id = id;
        this.homeTeamName = homeTeamName;
        this.awayTeamName = awayTeamName;
        this.status = Status.STARTED;
        this.startTime = startTime;
    }

    public void scoreHome() {
        homeScore++;
    }

    public void scoreAway() {
        awayScore++;
    }

    public void correctHome() {
        if (homeScore == 0) {
            throw new IllegalArgumentException("Score cannot be below zero");
        }
        homeScore--;
    }

    public void correctAway() {
        if (awayScore == 0) {
            throw new IllegalArgumentException("Score cannot be below zero");
        }
        awayScore--;
    }

    public void finish() {
        status = Status.FINISHED;
    }

    public MatchSummary toSummary() {
        return new MatchSummary(
                id, homeTeamName, awayTeamName,
                homeScore, awayScore, startTime);
    }

    public FinishedMatch toFinished(Instant finishedAt) {
        return new FinishedMatch(
                id, homeTeamName, awayTeamName,
                homeScore, awayScore, startTime, finishedAt);
    }

    public int totalScore() {
        return homeScore + awayScore;
    }

    public long startSequence() {
        return id.value();
    }
}
