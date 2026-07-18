package com.mc4.scoreboard.client

import com.mc4.scoreboard.api.Scoreboard
import spock.lang.Specification

class ScoreboardSpec extends Specification {

    def "configured factory rejects null"() {
        when:
        Scoreboard.inMemory(null)

        then:
        thrown(NullPointerException)
    }
}
