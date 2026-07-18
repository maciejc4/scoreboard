package com.mc4.scoreboard

import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.lang.reflect.Modifier

class PublicApiSpec extends Specification {

    def "configuration builder supplies the default and validates custom limits"() {
        expect:
        ScoreboardConfig.builder().build().historyLimit() == 1_000
        ScoreboardConfig.builder().historyLimit(25).build().historyLimit() == 25

        when:
        ScoreboardConfig.builder().historyLimit(0).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "match summary exposes its derived total"() {
        given:
        def summary = new MatchSummary(new MatchId(7), "Mexico", "Canada", 2, 3, Instant.EPOCH)

        expect:
        summary.total() == 5
    }

    def "finished match exposes its derived duration"() {
        given:
        def finished = new FinishedMatch(
                new MatchId(8), "Spain", "Brazil", 1, 0,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(90))

        expect:
        finished.duration() == Duration.ofSeconds(90)
    }

    def "match id has value equality and opaque display"() {
        expect:
        new MatchId(42) == new MatchId(42)
        new MatchId(42) != new MatchId(43)
        new MatchId(42).toString() == "Match#42"
    }

    def "match id cannot be constructed outside its package"() {
        expect:
        !Modifier.isPublic(MatchId.declaredConstructors.first().modifiers)
    }

    def "configured factory rejects null before implementation is available"() {
        when:
        Scoreboard.inMemory(null)

        then:
        thrown(NullPointerException)
    }
}
