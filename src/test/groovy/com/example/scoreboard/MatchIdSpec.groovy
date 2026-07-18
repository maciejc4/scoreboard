package com.example.scoreboard

import spock.lang.Specification

/**
 * Sanity spec verifying the toolchain: Groovy/Spock compile & run, and that
 * Lombok's generated {@code equals}/{@code hashCode} on {@link MatchId} work.
 */
class MatchIdSpec extends Specification {

    def "ids with the same value are equal (Lombok-generated equals/hashCode)"() {
        given:
        def a = new MatchId(1L)
        def b = new MatchId(1L)

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def "ids with different values are not equal"() {
        expect:
        new MatchId(1L) != new MatchId(2L)
    }
}
