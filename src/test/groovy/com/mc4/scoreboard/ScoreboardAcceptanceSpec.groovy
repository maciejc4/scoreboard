package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.Scoreboard
import com.mc4.scoreboard.api.config.ScoreboardConfig
import com.mc4.scoreboard.api.exception.ScoreboardException
import com.mc4.scoreboard.api.match.MatchId
import com.mc4.scoreboard.api.match.MatchNotFoundException
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(20)
class ScoreboardAcceptanceSpec extends Specification {

    def "AT-01 start creates a zero-zero match"() {
        given: def board = Scoreboard.inMemory()
        when: def id = board.startMatch("Mexico", "Canada")
        then:
        id != null
        with(board.getMatch(id).orElseThrow()) {
            homeTeam() == "Mexico"
            awayTeam() == "Canada"
            homeScore() == 0
            awayScore() == 0
        }
        board.getSummary()*.matchId() == [id]
    }

    def "AT-02 each start yields a distinct id"() {
        given: def board = Scoreboard.inMemory()
        when:
        def first = board.startMatch("Spain", "Brazil")
        def second = board.startMatch("Spain", "Brazil")
        then:
        first != second
        board.getSummary()*.matchId().toSet() == [first, second] as Set
    }

    def "AT-03 same team may play in simultaneous matches"() {
        given: def board = Scoreboard.inMemory()
        when:
        def first = board.startMatch("Spain", "Brazil")
        def second = board.startMatch("Spain", "Italy")
        then:
        board.getMatch(first).present
        board.getMatch(second).present
    }

    def "AT-04 startedAt is populated"() {
        given: def board = Scoreboard.inMemory()
        when: def id = board.startMatch("A", "B")
        then: board.getMatch(id).orElseThrow().startedAt() instanceof Instant
    }

    def "AT-05 home goals increment home score"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        when: 3.times { board.recordHomeGoal(id) }
        then: score(board, id) == [3, 0]
    }

    def "AT-06 away goals increment away score"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        when: 5.times { board.recordAwayGoal(id) }
        then: score(board, id) == [0, 5]
    }

    def "AT-07 home and away scores are independent"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        when:
        10.times { board.recordHomeGoal(id) }
        2.times { board.recordAwayGoal(id) }
        then: score(board, id) == [10, 2]
    }

    def "AT-08 home correction decrements by one"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        3.times { board.recordHomeGoal(id) }
        when: board.correctHomeGoal(id)
        then: score(board, id) == [2, 0]
    }

    def "AT-09 correction below zero throws and is atomic"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        when: board.correctHomeGoal(id)
        then: thrown(IllegalArgumentException)
        and: score(board, id) == [0, 0]
    }

    def "AT-10 away correction is symmetric"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        board.recordAwayGoal(id)
        when: board.correctAwayGoal(id)
        then: score(board, id) == [0, 0]
        when: board.correctAwayGoal(id)
        then: thrown(IllegalArgumentException)
        and: score(board, id) == [0, 0]
    }

    def "AT-11 finish moves a match from summary to history"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("Mexico", "Canada")
        5.times { board.recordAwayGoal(id) }
        when: board.finishMatch(id)
        then: !board.getSummary()*.matchId().contains(id)
        and:
        with(board.getHistory().find { it.matchId() == id }) {
            homeScore() == 0
            awayScore() == 5
            finishedAt() instanceof Instant
        }
    }

    @Unroll
    def "AT-12 #operation on a finished match throws"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        board.finishMatch(id)
        when: action(board, id)
        then: thrown(MatchNotFoundException)
        where:
        operation          | action
        "recordHomeGoal"   | { b, match -> b.recordHomeGoal(match) }
        "correctHomeGoal"  | { b, match -> b.correctHomeGoal(match) }
        "finishMatch"      | { b, match -> b.finishMatch(match) }
    }

    def "AT-13 getMatch returns empty for a finished id"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        board.finishMatch(id)
        expect: board.getMatch(id).empty
    }

    def "AT-14 canonical worked example has specified ordering"() {
        given:
        def board = Scoreboard.inMemory()
        def matches = [
                startAndScore(board, "Mexico", "Canada", 0, 5),
                startAndScore(board, "Spain", "Brazil", 10, 2),
                startAndScore(board, "Germany", "France", 2, 2),
                startAndScore(board, "Uruguay", "Italy", 6, 6),
                startAndScore(board, "Argentina", "Australia", 3, 1)
        ]
        expect:
        board.getSummary()*.matchId() == [matches[3], matches[1], matches[0], matches[4], matches[2]]
    }

    def "AT-15 total score descending is the primary ordering key"() {
        given:
        def board = Scoreboard.inMemory()
        def lower = startAndScore(board, "A", "B", 2, 3)
        def higher = startAndScore(board, "C", "D", 6, 6)
        expect: board.getSummary()*.matchId() == [higher, lower]
    }

    def "AT-16 ties are ordered by most recent start"() {
        given:
        def board = Scoreboard.inMemory()
        def earlier = startAndScore(board, "A", "B", 1, 1)
        def later = startAndScore(board, "C", "D", 2, 0)
        expect: board.getSummary()*.matchId() == [later, earlier]
    }

    def "AT-17 an empty board has an empty non-null summary"() {
        expect:
        Scoreboard.inMemory().getSummary() != null
        Scoreboard.inMemory().getSummary().empty
    }

    def "AT-18 getMatch returns current state"() {
        given:
        def board = Scoreboard.inMemory()
        def id = startAndScore(board, "A", "B", 2, 1)
        expect: score(board, id) == [2, 1]
    }

    def "AT-19 getMatch returns empty for an id issued by another board"() {
        given:
        def board = Scoreboard.inMemory()
        def unrelatedId = Scoreboard.inMemory().startMatch("A", "B")
        expect: board.getMatch(unrelatedId).empty
    }

    def "AT-20 history is ordered most recently finished first"() {
        given:
        def board = Scoreboard.inMemory()
        def ids = (1..3).collect { board.startMatch("H$it", "A$it") }
        ids.each { board.finishMatch(it) }
        expect: board.getHistory()*.matchId() == ids.reverse()
    }

    def "AT-21 finished match carries its full payload"() {
        given:
        def board = Scoreboard.inMemory()
        def id = startAndScore(board, "Alpha", "Beta", 1, 2)
        def expectedStartedAt = board.getMatch(id).orElseThrow().startedAt()
        when:
        board.finishMatch(id)
        def result = board.getHistory().first()
        then:
        with(result) {
            matchId() == id
            homeTeam() == "Alpha"
            awayTeam() == "Beta"
            homeScore() == 1
            awayScore() == 2
            startedAt() == expectedStartedAt
            finishedAt() instanceof Instant
            duration() == Duration.between(startedAt(), finishedAt())
        }
    }

    def "AT-22 history cap evicts the oldest match"() {
        given:
        def board = Scoreboard.inMemory(ScoreboardConfig.builder().historyLimit(2).build())
        def ids = (1..3).collect { board.startMatch("H$it", "A$it") }
        ids.each { board.finishMatch(it) }
        expect: board.getHistory()*.matchId() == [ids[2], ids[1]]
    }

    def "AT-23 clearHistory leaves live matches untouched"() {
        given:
        def board = Scoreboard.inMemory()
        def finished = board.startMatch("A", "B")
        def live = board.startMatch("C", "D")
        board.finishMatch(finished)
        when: board.clearHistory()
        then: board.getHistory().empty
        and: board.getSummary()*.matchId() == [live]
    }

    @Unroll
    def "AT-24 null #side team name throws NPE"() {
        when: Scoreboard.inMemory().startMatch(home, away)
        then: thrown(NullPointerException)
        where:
        side   | home      | away
        "home" | null      | "France"
        "away" | "Germany" | null
    }

    @Unroll
    def "AT-25 blank team name '#name' throws IAE"() {
        when: Scoreboard.inMemory().startMatch(name, "France")
        then: thrown(IllegalArgumentException)
        where: name << ["   ", ""]
    }

    def "AT-26 exact self-play is rejected"() {
        when: Scoreboard.inMemory().startMatch("Spain", "Spain")
        then: thrown(IllegalArgumentException)
    }

    def "AT-27 normalized self-play is rejected"() {
        when: Scoreboard.inMemory().startMatch("Spain", "  spain ")
        then: thrown(IllegalArgumentException)
    }

    def "AT-28 display preserves original casing"() {
        given: def board = Scoreboard.inMemory()
        when: def match = board.getMatch(board.startMatch("Mexico", "Canada")).orElseThrow()
        then:
        match.homeTeam() == "Mexico"
        match.awayTeam() == "Canada"
    }

    def "AT-29 display names are trimmed"() {
        given: def board = Scoreboard.inMemory()
        when: def match = board.getMatch(board.startMatch("  Mexico  ", "Canada")).orElseThrow()
        then: match.homeTeam() == "Mexico"
    }

    def "AT-30 normalization is independent of the default locale"() {
        given:
        def previousLocale = Locale.default
        Locale.default = Locale.forLanguageTag("tr-TR")
        when: Scoreboard.inMemory().startMatch("istanbul", "ISTANBUL")
        then: thrown(IllegalArgumentException)
        cleanup: Locale.default = previousLocale
    }

    @Unroll
    def "AT-31 #operation rejects an unknown id"() {
        given:
        def board = Scoreboard.inMemory()
        def unknownId = Scoreboard.inMemory().startMatch("A", "B")
        when: action(board, unknownId)
        then: thrown(MatchNotFoundException)
        where:
        operation          | action
        "recordHomeGoal"   | { b, id -> b.recordHomeGoal(id) }
        "recordAwayGoal"   | { b, id -> b.recordAwayGoal(id) }
        "correctHomeGoal"  | { b, id -> b.correctHomeGoal(id) }
        "correctAwayGoal"  | { b, id -> b.correctAwayGoal(id) }
        "finishMatch"      | { b, id -> b.finishMatch(id) }
    }

    @Unroll
    def "AT-32 #operation rejects a null MatchId"() {
        given: def board = Scoreboard.inMemory()
        when: action(board)
        then: thrown(NullPointerException)
        where:
        operation          | action
        "recordHomeGoal"   | { b -> b.recordHomeGoal(null) }
        "recordAwayGoal"   | { b -> b.recordAwayGoal(null) }
        "correctHomeGoal"  | { b -> b.correctHomeGoal(null) }
        "correctAwayGoal"  | { b -> b.correctAwayGoal(null) }
        "finishMatch"      | { b -> b.finishMatch(null) }
        "getMatch"         | { b -> b.getMatch(null) }
    }

    def "AT-33 MatchNotFoundException belongs to the public exception hierarchy"() {
        expect:
        ScoreboardException.isAssignableFrom(MatchNotFoundException)
        RuntimeException.isAssignableFrom(MatchNotFoundException)
    }

    @Unroll
    def "AT-34 summary does not support #operation"() {
        given:
        def board = Scoreboard.inMemory()
        board.startMatch("A", "B")
        def summary = board.getSummary()
        when: action(summary)
        then: thrown(UnsupportedOperationException)
        where:
        operation | action
        "add"     | { list -> list.add(list.first()) }
        "remove"  | { list -> list.remove(0) }
    }

    def "AT-35 summary is a stable point-in-time snapshot"() {
        given:
        def board = Scoreboard.inMemory()
        def first = board.startMatch("A", "B")
        def snapshot = board.getSummary()
        when:
        board.recordHomeGoal(first)
        board.startMatch("C", "D")
        then:
        snapshot.size() == 1
        snapshot.first().matchId() == first
        snapshot.first().homeScore() == 0
    }

    def "AT-36 history list is unmodifiable"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        board.finishMatch(id)
        def history = board.getHistory()
        when: history.remove(0)
        then: thrown(UnsupportedOperationException)
    }

    def "AT-37 default factory caps history at one thousand"() {
        given: def board = Scoreboard.inMemory()
        when:
        def ids = (0..1_000).collect { board.startMatch("H$it", "A$it") }
        ids.each { board.finishMatch(it) }
        then:
        board.getHistory().size() == 1_000
        board.getHistory()*.matchId() == ids.drop(1).reverse()
    }

    @Unroll
    def "AT-38 history limit #limit is rejected"() {
        when: ScoreboardConfig.builder().historyLimit(limit).build()
        then: thrown(IllegalArgumentException)
        where: limit << [0, -1]
    }

    def "AT-39 null config is rejected"() {
        when: Scoreboard.inMemory(null)
        then: thrown(NullPointerException)
    }

    def "AT-40 concurrent goals do not lose updates"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        int threads = 8
        int goalsPerThread = 250
        when:
        concurrently(threads) { goalsPerThread.times { board.recordHomeGoal(id) } }
        then: board.getMatch(id).orElseThrow().homeScore() == threads * goalsPerThread
    }

    def "AT-41 finish is atomic with respect to readers"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("A", "B")
        def stop = new CountDownLatch(1)
        def errors = new ConcurrentLinkedQueue<Throwable>()
        def observations = new ConcurrentLinkedQueue<List<Boolean>>()
        def pool = Executors.newFixedThreadPool(5)
        4.times {
            pool.submit {
                try {
                    while (stop.count > 0) {
                        def liveBefore = board.getSummary()*.matchId().contains(id)
                        def finished = board.getHistory()*.matchId().contains(id)
                        def liveAfter = board.getSummary()*.matchId().contains(id)
                        observations.add([liveBefore, finished, liveAfter])
                    }
                } catch (Throwable error) {
                    errors.add(error)
                }
            }
        }
        when:
        def finisher = pool.submit { board.finishMatch(id) }
        finisher.get(10, TimeUnit.SECONDS)
        stop.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        then:
        errors.empty
        !observations.any { it == [true, true, true] }
        !board.getSummary()*.matchId().contains(id)
        board.getHistory()*.matchId().count(id) == 1
    }

    def "AT-42 concurrent starts produce unique ids"() {
        given:
        def board = Scoreboard.inMemory()
        int count = 200
        when:
        def ids = concurrentlyCollect(count) { int i -> board.startMatch("H$i", "A$i") }
        then:
        ids.toSet().size() == count
        board.getSummary().size() == count
    }

    def "AT-43 concurrent readers can safely iterate snapshots during mutation"() {
        given:
        def board = Scoreboard.inMemory()
        def ids = (0..<100).collect { board.startMatch("H$it", "A$it") }
        def errors = new ConcurrentLinkedQueue<Throwable>()
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(8)
        when:
        def futures = (0..<8).collect { int worker ->
            pool.submit {
                start.await()
                try {
                    if (worker < 4) {
                        200.times {
                            board.getSummary().each { it.total() }
                            board.getHistory().each { it.duration() }
                        }
                    } else {
                        ids.findAll { it.hashCode() % 4 == worker - 4 }.each {
                            board.recordHomeGoal(it)
                            board.finishMatch(it)
                        }
                    }
                } catch (Throwable error) {
                    errors.add(error)
                }
            }
        }
        start.countDown()
        futures*.get(10, TimeUnit.SECONDS)
        pool.shutdown()
        then: errors.empty
    }

    private static List<Integer> score(Scoreboard board, MatchId id) {
        def match = board.getMatch(id).orElseThrow()
        [match.homeScore(), match.awayScore()]
    }

    private static MatchId startAndScore(Scoreboard board, String home, String away,
                                         int homeGoals, int awayGoals) {
        def id = board.startMatch(home, away)
        homeGoals.times { board.recordHomeGoal(id) }
        awayGoals.times { board.recordAwayGoal(id) }
        id
    }

    private static void concurrently(int count, Closure<?> task) {
        concurrentlyCollect(count) { task.call() }
    }

    private static List concurrentlyCollect(int count, Closure<?> task) {
        def ready = new CountDownLatch(count)
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(count)
        try {
            def futures = (0..<count).collect { int index ->
                pool.submit({
                    ready.countDown()
                    start.await()
                    task.maximumNumberOfParameters == 0 ? task.call() : task.call(index)
                } as Callable)
            }
            assert ready.await(10, TimeUnit.SECONDS)
            start.countDown()
            futures.collect { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
