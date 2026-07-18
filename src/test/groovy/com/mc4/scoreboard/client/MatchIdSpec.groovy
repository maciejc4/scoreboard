package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.Scoreboard
import spock.lang.Specification

class MatchIdSpec extends Specification {

    def "match id is an opaque stable handle issued by the scoreboard"() {
        given:
        def board = Scoreboard.inMemory()

        when:
        def id = board.startMatch("Mexico", "Canada")
        def idFromLookup = board.getMatch(id).orElseThrow().matchId()

        then:
        id == idFromLookup
        id.hashCode() == idFromLookup.hashCode()
        id.toString()
    }

    def "separately started matches receive unequal handles"() {
        given:
        def board = Scoreboard.inMemory()

        expect:
        board.startMatch("Spain", "Brazil") != board.startMatch("Spain", "Italy")
    }
}
