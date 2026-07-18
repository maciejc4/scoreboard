package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.Scoreboard
import spock.lang.Specification

import java.time.Duration

class FinishedMatchSpec extends Specification {

    def "finished match exposes its derived duration"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("Spain", "Brazil")

        when:
        board.finishMatch(id)
        def finished = board.getHistory().find { it.matchId() == id }

        then:
        finished != null
        finished.duration() == Duration.between(finished.startedAt(), finished.finishedAt())
    }
}
