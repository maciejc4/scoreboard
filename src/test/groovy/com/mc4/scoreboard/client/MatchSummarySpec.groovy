package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.Scoreboard
import spock.lang.Specification

class MatchSummarySpec extends Specification {

    def "match summary exposes the current score and its derived total"() {
        given:
        def board = Scoreboard.inMemory()
        def id = board.startMatch("Mexico", "Canada")
        2.times { board.recordHomeGoal(id) }
        3.times { board.recordAwayGoal(id) }

        when:
        def summary = board.getMatch(id).orElseThrow()

        then:
        summary.homeScore() == 2
        summary.awayScore() == 3
        summary.total() == 5
    }
}
