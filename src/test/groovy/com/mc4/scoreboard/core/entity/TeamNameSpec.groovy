package com.mc4.scoreboard.core.entity

import spock.lang.Specification
import spock.lang.Unroll

class TeamNameSpec extends Specification {

    def "stores trimmed original and locale-independent normalized forms"() {
        given:
        def previousLocale = Locale.default
        Locale.default = Locale.forLanguageTag("tr-TR")

        when:
        def teamName = new TeamName("  istanbul  ")

        then:
        teamName.original() == "istanbul"
        teamName.normalized() == "ISTANBUL"

        cleanup:
        Locale.default = previousLocale
    }

    def "equality and hash code use only the normalized form"() {
        given:
        def first = new TeamName(" Spain ")
        def second = new TeamName("SPAIN")

        expect:
        first == second
        first.hashCode() == second.hashCode()
        first.original() == "Spain"
        second.original() == "SPAIN"
    }

    @Unroll
    def "rejects invalid value #value"() {
        when:
        new TeamName(value)

        then:
        thrown(expectedException)

        where:
        value | expectedException
        null  | NullPointerException
        ""    | IllegalArgumentException
        "   " | IllegalArgumentException
    }
}
