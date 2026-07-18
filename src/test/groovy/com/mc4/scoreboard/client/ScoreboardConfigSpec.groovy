package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.config.ScoreboardConfig
import spock.lang.Specification
import spock.lang.Unroll

class ScoreboardConfigSpec extends Specification {

    def "configuration builder supplies the documented default"() {
        expect:
        ScoreboardConfig.builder().build().historyLimit() == 1_000
    }

    def "configuration builder accepts a valid custom history limit"() {
        expect:
        ScoreboardConfig.builder().historyLimit(25).build().historyLimit() == 25
    }

    @Unroll
    def "configuration builder rejects history limit #limit"() {
        when:
        ScoreboardConfig.builder().historyLimit(limit).build()

        then:
        thrown(IllegalArgumentException)

        where:
        limit << [0, -1]
    }
}
